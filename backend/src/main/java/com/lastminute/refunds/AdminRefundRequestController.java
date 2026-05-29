package com.lastminute.refunds;

import com.lastminute.bookings.Booking;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Admin queue + actions for refund requests. Gated by ROLE_ADMIN in SecurityConfig. */
@RestController
@RequestMapping("/api/admin/refund-requests")
public class AdminRefundRequestController {

  private final RefundRequestRepository requests;
  private final Clock clock;

  public AdminRefundRequestController(RefundRequestRepository requests, Clock clock) {
    this.requests = requests;
    this.clock = clock;
  }

  @GetMapping
  @Transactional(readOnly = true)
  public List<AdminRefundRequestDto> list(
      @RequestParam(required = false, defaultValue = "open") RefundRequestStatus status) {
    return requests.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
        .filter(r -> r.getStatus() == status)
        .map(AdminRefundRequestDto::from)
        .toList();
  }

  public record DenyBody(@NotBlank @Size(max = 2000) String notes) {}

  @PostMapping("/{id}/deny")
  @Transactional
  public AdminRefundRequestDto deny(@PathVariable UUID id, @Valid @RequestBody DenyBody body) {
    RefundRequest r =
        requests
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
    if (r.getStatus() != RefundRequestStatus.open) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "ALREADY_RESOLVED");
    }
    r.setStatus(RefundRequestStatus.denied);
    r.setAdminNotes(body.notes());
    r.setResolvedAt(Instant.now(clock));
    return AdminRefundRequestDto.from(requests.saveAndFlush(r));
  }

  public record NotesBody(@NotBlank @Size(max = 2000) String notes) {}

  /** Append-or-replace admin notes without resolving. */
  @PostMapping("/{id}/notes")
  @Transactional
  public AdminRefundRequestDto notes(@PathVariable UUID id, @Valid @RequestBody NotesBody body) {
    RefundRequest r =
        requests
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
    r.setAdminNotes(body.notes());
    return AdminRefundRequestDto.from(requests.saveAndFlush(r));
  }

  public record AdminRefundRequestDto(
      UUID id,
      UUID bookingId,
      String listingTitle,
      String consumerEmail,
      RefundReason reason,
      String details,
      RefundRequestStatus status,
      String adminNotes,
      int amountPaidCents,
      String currency,
      String stripePaymentIntentId,
      Instant createdAt,
      Instant resolvedAt) {
    public static AdminRefundRequestDto from(RefundRequest r) {
      Booking b = r.getBooking();
      return new AdminRefundRequestDto(
          r.getId(),
          b.getId(),
          b.getListing().getTitle(),
          r.getConsumer().getEmail(),
          r.getReason(),
          r.getDetails(),
          r.getStatus(),
          r.getAdminNotes(),
          b.getAmountPaidCents(),
          b.getCurrency(),
          b.getStripePaymentIntentId(),
          r.getCreatedAt(),
          r.getResolvedAt());
    }
  }
}
