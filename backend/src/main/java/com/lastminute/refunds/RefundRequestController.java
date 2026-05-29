package com.lastminute.refunds;

import com.lastminute.auth.CurrentUser;
import com.lastminute.auth.ResendClient;
import com.lastminute.bookings.Booking;
import com.lastminute.bookings.BookingRepository;
import com.lastminute.bookings.BookingStatus;
import com.lastminute.users.User;
import com.lastminute.users.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
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
 * Spec §6.4 — consumer self-service refund request. Files an {@code open} request and emails
 * ops. Admin handles the actual refund through the Stripe Dashboard; the resulting
 * {@code charge.refunded} webhook auto-closes the request via {@link RefundRequestAutoCloser}.
 */
@RestController
@RequestMapping("/api/bookings/{bookingId}/refund-request")
public class RefundRequestController {

  private static final Logger LOG = LoggerFactory.getLogger(RefundRequestController.class);

  private final RefundRequestRepository requests;
  private final BookingRepository bookings;
  private final UserRepository users;
  private final ResendClient email;
  private final Clock clock;
  private final String adminEmail;

  public RefundRequestController(
      RefundRequestRepository requests,
      BookingRepository bookings,
      UserRepository users,
      ResendClient email,
      Clock clock,
      @Value("${app.admin.email:admin@local}") String adminEmail) {
    this.requests = requests;
    this.bookings = bookings;
    this.users = users;
    this.email = email;
    this.clock = clock;
    this.adminEmail = adminEmail;
  }

  public record FileBody(
      @NotNull RefundReason reason,
      @Size(max = 2000) String details) {}

  public record FiledResult(
      UUID requestId, RefundRequestStatus status, Instant createdAt, String message) {}

  @PostMapping
  @Transactional
  public FiledResult file(
      @AuthenticationPrincipal CurrentUser principal,
      @PathVariable UUID bookingId,
      @Valid @RequestBody FileBody body) {
    Booking b =
        bookings
            .findById(bookingId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
    if (!b.getConsumer().getId().equals(principal.id())) {
      // Don't leak whether the booking exists for someone else.
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found");
    }
    if (b.getStatus() == BookingStatus.cancelled) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "ALREADY_REFUNDED");
    }
    if (b.getStatus() == BookingStatus.pending) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "NOT_PAID_YET");
    }

    // Fast-path: existing open request → return it. The partial unique index also catches
    // races so we don't double-file even under concurrent submission.
    var existing = requests.findOpenForBooking(bookingId, RefundRequestStatus.open);
    if (existing.isPresent()) {
      RefundRequest r = existing.get();
      return new FiledResult(
          r.getId(),
          r.getStatus(),
          r.getCreatedAt(),
          "Existing open request — we'll get back to you within 1 business day.");
    }

    User consumer =
        users
            .findById(principal.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no_user"));

    RefundRequest r = new RefundRequest();
    r.setBooking(b);
    r.setConsumer(consumer);
    r.setReason(body.reason());
    r.setDetails(body.details());
    r.setStatus(RefundRequestStatus.open);
    try {
      requests.saveAndFlush(r);
    } catch (DataIntegrityViolationException race) {
      // Lost the race against another submission; surface the winning one.
      RefundRequest winner =
          requests
              .findOpenForBooking(bookingId, RefundRequestStatus.open)
              .orElseThrow(() -> race);
      return new FiledResult(
          winner.getId(),
          winner.getStatus(),
          winner.getCreatedAt(),
          "Existing open request — we'll get back to you within 1 business day.");
    }

    try {
      email.sendRefundRequestFiled(
          adminEmail,
          consumer.getEmail(),
          b.getListing().getTitle(),
          body.reason().name(),
          body.details(),
          b.getId().toString(),
          b.getAmountPaidCents(),
          b.getCurrency());
    } catch (Exception e) {
      // Don't fail the consumer's request because we can't notify ops.
      LOG.error("admin-notify email failed for refund request {}", r.getId(), e);
    }

    return new FiledResult(
        r.getId(),
        r.getStatus(),
        Instant.now(clock),
        "Refund request filed. We'll review within 1 business day.");
  }

  @GetMapping
  @Transactional(readOnly = true)
  public List<MyRefundRequestDto> mine(
      @AuthenticationPrincipal CurrentUser principal, @PathVariable UUID bookingId) {
    // Defensive: confirm the booking belongs to this consumer
    Booking b =
        bookings
            .findById(bookingId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
    if (!b.getConsumer().getId().equals(principal.id())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found");
    }
    return requests.findAllByConsumer_IdOrderByCreatedAtDesc(principal.id()).stream()
        .filter(r -> r.getBooking().getId().equals(bookingId))
        .map(MyRefundRequestDto::from)
        .toList();
  }

  public record MyRefundRequestDto(
      UUID id,
      RefundReason reason,
      RefundRequestStatus status,
      String adminNotes,
      Instant createdAt,
      Instant resolvedAt) {
    public static MyRefundRequestDto from(RefundRequest r) {
      return new MyRefundRequestDto(
          r.getId(),
          r.getReason(),
          r.getStatus(),
          r.getAdminNotes(),
          r.getCreatedAt(),
          r.getResolvedAt());
    }
  }
}
