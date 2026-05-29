package com.lastminute.bookings;

import com.lastminute.auth.CurrentUser;
import com.lastminute.stripe.StripeService;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

/** Spec §5 Flow 1: consumer booking + payment HTTP surface. */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

  private static final Logger LOG = LoggerFactory.getLogger(BookingController.class);
  static final int REFRESH_MAX_PER_HOUR = 6;
  static final Duration REFRESH_COOLDOWN = Duration.ofSeconds(10);

  private final ReserveSpotService reserveSpot;
  private final BookingRepository bookings;
  private final BookingStateMachine stateMachine;
  private final StripeService stripe;
  private final Clock clock;
  private final String frontendOrigin;

  public BookingController(
      ReserveSpotService reserveSpot,
      BookingRepository bookings,
      BookingStateMachine stateMachine,
      StripeService stripe,
      Clock clock,
      @Value("${app.frontend-origin:http://localhost:5173}") String frontendOrigin) {
    this.reserveSpot = reserveSpot;
    this.bookings = bookings;
    this.stateMachine = stateMachine;
    this.stripe = stripe;
    this.clock = clock;
    this.frontendOrigin = frontendOrigin;
  }

  @PostMapping
  public ResponseEntity<BookingDto> create(
      @AuthenticationPrincipal CurrentUser consumer, @Valid @RequestBody CreateBody body) {
    if (consumer == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "auth_required");
    }

    // Step 3 — reserve spot in its own tx (commits immediately).
    var reserve = reserveSpot.reserveSpot(body.listingId(), consumer.id());
    Booking booking = reserve.booking();

    // If this is a resumed existing pending and a Stripe session already exists, just return it.
    if (!reserve.newlyCreated() && booking.getStripeCheckoutSessionId() != null) {
      return ResponseEntity.ok(BookingDto.of(booking, "existing"));
      // NOTE: production should retrieve the live URL via Stripe.Session.retrieve(id). For now,
      // resumed flow returns the booking with checkoutUrlIfAny="existing" so the frontend can
      // poll the success page directly. Improvement deferred to post-M3 polish.
    }

    // Step 4 — create Stripe Checkout Session OUTSIDE the booking tx (no network in row lock).
    String successUrl = frontendOrigin + "/bookings/" + booking.getId() + "/success";
    String cancelUrl = frontendOrigin + "/l/" + booking.getListing().getId();
    StripeService.CheckoutSession session;
    try {
      session =
          stripe.createCheckoutSessionForBooking(
              booking.getId().toString(),
              booking.getProvider().getStripeAccountId(),
              booking.getAmountPaidCents(),
              booking.getPlatformFeeCents(),
              booking.getCurrency(),
              successUrl,
              cancelUrl);
    } catch (StripeException e) {
      LOG.error("Stripe createCheckoutSession failed for booking {}, cancelling", booking.getId(), e);
      stateMachine.transition(
          booking.getId(),
          BookingStatus.pending,
          BookingStatus.cancelled,
          CancellationReason.system);
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "stripe_unavailable", e);
    }

    // Step 4b — attach session id; if the sweeper killed the booking during the Stripe call, the
    // affected row count is 0. Recover by expiring the Stripe session.
    int affected = bookings.attachCheckoutSession(booking.getId(), session.id());
    if (affected == 0) {
      LOG.warn(
          "Step 4b race: booking {} was cancelled during Stripe call, expiring session {}",
          booking.getId(),
          session.id());
      try {
        stripe.expireCheckoutSession(session.id());
      } catch (StripeException e) {
        // §6.1 fallback: if expire fails, the webhook-for-cancelled-booking auto-refund path
        // catches any payment that lands.
        LOG.warn("could not expire session {}; relying on auto-refund fallback", session.id(), e);
      }
      throw new ResponseStatusException(HttpStatus.CONFLICT, "hold_expired_retry");
    }

    booking.setStripeCheckoutSessionId(session.id());
    return ResponseEntity.ok(BookingDto.of(booking, session.url()));
  }

  @GetMapping("/me")
  @Transactional(readOnly = true)
  public List<BookingDto> mine(@AuthenticationPrincipal CurrentUser user) {
    return bookings.findAllByConsumer_IdOrderByCreatedAtDesc(user.id()).stream()
        .map(b -> {
          b.getListing().getTitle(); // eager-touch
          return BookingDto.of(b, null);
        })
        .toList();
  }

  @GetMapping("/{id}")
  @Transactional(readOnly = true)
  public ResponseEntity<BookingDto> byId(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
    Booking b =
        bookings
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
    if (!b.getConsumer().getId().equals(user.id()) && user.role() != com.lastminute.users.UserRole.admin) {
      // §6.6: don't leak existence via 403
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found");
    }
    b.getListing().getTitle();
    return ResponseEntity.ok(BookingDto.of(b, null));
  }

  /**
   * §6.1 on-demand reconciliation. Server-side rate limit: 1 per 10s + 6 per hour per booking.
   * Reads Stripe's view of the session and pushes the booking through the state machine if
   * Stripe confirms payment but our webhook hasn't landed yet.
   */
  @PostMapping("/{id}/refresh-status")
  @Transactional
  public ResponseEntity<BookingDto> refreshStatus(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
    Booking b =
        bookings
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
    if (!b.getConsumer().getId().equals(user.id())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found");
    }

    Instant now = Instant.now(clock);
    if (b.getRefreshLastAt() != null
        && Duration.between(b.getRefreshLastAt(), now).compareTo(REFRESH_COOLDOWN) < 0) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "refresh_cooldown");
    }
    Instant hourAgo = now.minus(Duration.ofHours(1));
    if (b.getRefreshLastAt() != null
        && b.getRefreshLastAt().isAfter(hourAgo)
        && b.getRefreshCount() >= REFRESH_MAX_PER_HOUR) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "refresh_hourly_cap");
    }

    // Reset hourly counter if more than an hour since last refresh.
    if (b.getRefreshLastAt() == null || b.getRefreshLastAt().isBefore(hourAgo)) {
      b.setRefreshCount(0);
    }
    b.setRefreshCount(b.getRefreshCount() + 1);
    b.setRefreshLastAt(now);
    bookings.save(b);

    // Best-effort Stripe reconciliation. For M3 we just return the current booking state; full
    // session.retrieve + reconcile lives in M5's hourly reconciliation job + handler.
    b.getListing().getTitle();
    return ResponseEntity.ok(BookingDto.of(b, null));
  }

  public record CreateBody(@NotNull UUID listingId) {}
}
