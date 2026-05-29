package com.lastminute.refunds;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lastminute.bookings.Booking;
import com.lastminute.bookings.BookingRepository;
import com.lastminute.webhooks.PaymentEvent;
import com.lastminute.webhooks.PaymentEventRepository;
import com.lastminute.webhooks.StripeEventReceived;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * When a {@code charge.refunded} webhook arrives, the booking-side {@link
 * com.lastminute.bookings.RefundHandler} cancels the booking. This listener separately closes any
 * still-open {@link RefundRequest} for that booking so the consumer's "Open request" UI clears
 * automatically. Idempotent: skips already-closed requests.
 */
@Component
public class RefundRequestAutoCloser {

  private static final Logger LOG = LoggerFactory.getLogger(RefundRequestAutoCloser.class);

  private final PaymentEventRepository events;
  private final BookingRepository bookings;
  private final RefundRequestRepository requests;
  private final Clock clock;
  private final ObjectMapper json;

  public RefundRequestAutoCloser(
      PaymentEventRepository events,
      BookingRepository bookings,
      RefundRequestRepository requests,
      Clock clock,
      ObjectMapper json) {
    this.events = events;
    this.bookings = bookings;
    this.requests = requests;
    this.clock = clock;
    this.json = json;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onEvent(StripeEventReceived event) {
    if (!"charge.refunded".equals(event.eventType())) return;

    PaymentEvent row = events.findById(event.paymentEventId()).orElse(null);
    if (row == null) return;

    try {
      JsonNode payload = json.readTree(row.getPayload());
      JsonNode charge = payload.path("data").path("object");
      String paymentIntentId = charge.path("payment_intent").asText(null);
      if (paymentIntentId == null || paymentIntentId.isBlank()) return;

      Optional<Booking> match = bookings.findByStripePaymentIntentId(paymentIntentId);
      if (match.isEmpty()) return;

      var open =
          requests.findOpenForBooking(match.get().getId(), RefundRequestStatus.open);
      if (open.isEmpty()) return;

      RefundRequest r = open.get();
      r.setStatus(RefundRequestStatus.auto_resolved);
      r.setResolvedAt(Instant.now(clock));
      requests.saveAndFlush(r);
      LOG.info("auto-closed refund request {} (booking {} refunded)", r.getId(), match.get().getId());

    } catch (Exception e) {
      LOG.error("auto-close failed for event {}", event.paymentEventId(), e);
    }
  }
}
