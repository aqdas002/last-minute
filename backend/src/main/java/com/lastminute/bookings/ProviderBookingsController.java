package com.lastminute.bookings;

import com.lastminute.auth.CurrentUser;
import com.lastminute.providers.Provider;
import com.lastminute.providers.ProviderRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Spec §5 Flow 3: provider side of redemption. {@code today} returns confirmed bookings whose
 * {@code start_time} is within today in the provider's timezone. {@code POST redeem} flips one
 * from confirmed → completed and stamps {@code redeemed_at}.
 */
@RestController
@RequestMapping("/api/providers/me/bookings")
public class ProviderBookingsController {

  private final BookingRepository bookings;
  private final ProviderRepository providers;
  private final BookingStateMachine stateMachine;
  private final Clock clock;

  public ProviderBookingsController(
      BookingRepository bookings,
      ProviderRepository providers,
      BookingStateMachine stateMachine,
      Clock clock) {
    this.bookings = bookings;
    this.providers = providers;
    this.stateMachine = stateMachine;
    this.clock = clock;
  }

  @GetMapping("/today")
  @Transactional(readOnly = true)
  public List<ProviderBookingDto> today(@AuthenticationPrincipal CurrentUser principal) {
    Provider p =
        providers
            .findById(principal.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no_provider"));
    ZoneId zone = ZoneId.of(p.getTimezone());
    LocalDate today = LocalDate.now(clock.withZone(zone));
    Instant from = today.atStartOfDay(zone).toInstant();
    Instant to = today.plusDays(1).atStartOfDay(zone).toInstant();

    return bookings.findTodaysConfirmed(principal.id(), BookingStatus.confirmed, from, to).stream()
        .map(ProviderBookingDto::from)
        .toList();
  }

  @GetMapping("/all")
  @Transactional(readOnly = true)
  public List<ProviderBookingDto> all(@AuthenticationPrincipal CurrentUser principal) {
    return bookings.findRecentByProvider(principal.id()).stream()
        .map(ProviderBookingDto::from)
        .toList();
  }

  /** 30-day revenue summary for the dashboard. Counts + sum payout in minor units. */
  @GetMapping("/summary")
  @Transactional(readOnly = true)
  public RevenueSummary summary(@AuthenticationPrincipal CurrentUser principal) {
    Provider p =
        providers
            .findById(principal.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no_provider"));
    Instant since = Instant.now(clock).minus(java.time.Duration.ofDays(30));
    var earning = List.of(BookingStatus.confirmed, BookingStatus.completed);
    long payout = bookings.sumEarnedSince(principal.id(), earning, since);
    long earningCount = bookings.countByProviderAndStatusesSince(principal.id(), earning, since);
    long cancelledCount =
        bookings.countByProviderAndStatusesSince(
            principal.id(), List.of(BookingStatus.cancelled), since);
    return new RevenueSummary(payout, p.getCurrency(), earningCount, cancelledCount, 30);
  }

  public record RevenueSummary(
      long payoutCents, String currency, long bookingsCount, long cancelledCount, int windowDays) {}

  /**
   * Spec §3.4: provider marks a confirmed booking as no-show after start_time has passed.
   * Does NOT auto-refund — booking moves confirmed → no_show. Consumer can still file a refund
   * request (§6.4) if they believe this is wrong; admin reviews via /admin/refunds.
   */
  @PostMapping("/{id}/mark-no-show")
  @Transactional
  public ResponseEntity<MarkResult> markNoShow(
      @AuthenticationPrincipal CurrentUser principal, @PathVariable("id") UUID bookingId) {
    Booking b =
        bookings
            .findById(bookingId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
    if (!b.getProvider().getId().equals(principal.id())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found");
    }
    if (b.getStatus() == BookingStatus.no_show) {
      return ResponseEntity.ok(new MarkResult("ALREADY_MARKED", b.getId()));
    }
    if (b.getStatus() != BookingStatus.confirmed) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "NOT_CONFIRMED");
    }
    // Require start_time to be in the past to prevent premature no-show marks.
    if (b.getListing().getStartTime().isAfter(Instant.now(clock))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "NOT_YET_STARTED");
    }
    stateMachine.transition(b.getId(), BookingStatus.confirmed, BookingStatus.no_show, null);
    return ResponseEntity.ok(new MarkResult("OK", b.getId()));
  }

  public record MarkResult(String code, UUID bookingId) {}

  @PostMapping("/redeem")
  @Transactional
  public ResponseEntity<RedemptionResult> redeem(
      @AuthenticationPrincipal CurrentUser principal, @Valid @RequestBody RedeemBody body) {
    Booking b =
        bookings
            .findActiveByCodeAndProvider(
                body.code(),
                principal.id(),
                List.of(BookingStatus.confirmed))
            .orElse(null);

    if (b == null) {
      // Maybe already redeemed?
      var anyMatch =
          bookings.findRecentByProvider(principal.id()).stream()
              .filter(x -> body.code().equalsIgnoreCase(x.getRedemptionCode()))
              .findFirst();
      if (anyMatch.isPresent() && anyMatch.get().getRedeemedAt() != null) {
        // §6.2 already-redeemed UX: return 409 with the redeemed_at timestamp
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(
                new RedemptionResult(
                    "ALREADY_REDEEMED",
                    anyMatch.get().getId(),
                    anyMatch.get().getRedeemedAt(),
                    null));
      }
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(new RedemptionResult("CODE_NOT_VALID", null, null, null));
    }

    Instant now = Instant.now(clock);
    b.setRedeemedAt(now);
    bookings.save(b);
    stateMachine.transition(b.getId(), BookingStatus.confirmed, BookingStatus.completed, null);

    return ResponseEntity.ok(
        new RedemptionResult("OK", b.getId(), now, b.getListing().getTitle()));
  }

  public record RedeemBody(@NotBlank @Size(min = 8, max = 8) String code) {}

  public record RedemptionResult(String code, UUID bookingId, Instant redeemedAt, String listingTitle) {}

  public record ProviderBookingDto(
      UUID id,
      UUID listingId,
      String listingTitle,
      String consumerEmail,
      BookingStatus status,
      Instant startTime,
      Instant confirmedAt,
      Instant redeemedAt,
      String redemptionCode,
      int amountPaidCents,
      int providerPayoutCents,
      String currency) {

    public static ProviderBookingDto from(Booking b) {
      return new ProviderBookingDto(
          b.getId(),
          b.getListing().getId(),
          b.getListing().getTitle(),
          b.getConsumer() != null ? b.getConsumer().getEmail() : "",
          b.getStatus(),
          b.getListing().getStartTime(),
          b.getConfirmedAt(),
          b.getRedeemedAt(),
          b.getRedemptionCode(),
          b.getAmountPaidCents(),
          b.getProviderPayoutCents(),
          b.getCurrency());
    }
  }
}
