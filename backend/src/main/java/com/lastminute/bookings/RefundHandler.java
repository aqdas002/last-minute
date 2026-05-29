package com.lastminute.bookings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Spec §5 Flow 4: refund pipeline. Stripe sends {@code charge.refunded} when the provider (or our
 * support team) issues a refund. We move the booking to {@code cancelled} with reason
 * {@code refund}. Works from any non-terminal status — confirmed bookings cancel, completed
 * bookings get a post-redemption "we still cancelled and refunded" record.
 */
@Component
public class RefundHandler {

  private static final Logger LOG = LoggerFactory.getLogger(RefundHandler.class);

  private final PaymentEventRepository events;
  private final BookingRepository bookings;
  private final BookingStateMachine stateMachine;
  private final Clock clock;
  private final ObjectMapper json;

  public RefundHandler(
      PaymentEventRepository events,
      BookingRepository bookings,
      BookingStateMachine stateMachine,
      Clock clock,
      ObjectMapper json) {
    this.events = events;
    this.bookings = bookings;
    this.stateMachine = stateMachine;
    this.clock = clock;
    this.json = json;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onEvent(StripeEventReceived event) {
    if (!"charge.refunded".equals(event.eventType())) return;

    PaymentEvent row = events.findById(event.paymentEventId()).orElse(null);
    if (row == null || row.getProcessedAt() != null) return;

    try {
      JsonNode payload = json.readTree(row.getPayload());
      JsonNode charge = payload.path("data").path("object");
      String paymentIntentId = charge.path("payment_intent").asText(null);

      if (paymentIntentId == null || paymentIntentId.isBlank()) {
        LOG.warn("charge.refunded without payment_intent; marking processed");
        markProcessed(row);
        return;
      }

      Optional<Booking> match = bookings.findByStripePaymentIntentId(paymentIntentId);
      if (match.isEmpty()) {
        LOG.warn("charge.refunded for unknown payment_intent {}; marking processed", paymentIntentId);
        markProcessed(row);
        return;
      }

      Booking b = match.get();
      if (b.getStatus() == BookingStatus.cancelled) {
        LOG.debug("booking {} already cancelled (refund replay)", b.getId());
        markProcessed(row);
        return;
      }
      stateMachine.transition(b.getId(), b.getStatus(), BookingStatus.cancelled, CancellationReason.refund);
      LOG.info("booking {} cancelled via refund (from {})", b.getId(), b.getStatus());
      markProcessed(row);

    } catch (Exception e) {
      LOG.error("failed to process refund event {}", event.paymentEventId(), e);
      row.setProcessingError(e.getMessage());
      events.save(row);
    }
  }

  private void markProcessed(PaymentEvent row) {
    row.setProcessedAt(Instant.now(clock));
    events.saveAndFlush(row);
  }
}
