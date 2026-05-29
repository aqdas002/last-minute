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
 * Spec §5 Flow 4: chargeback. Stripe sends {@code charge.dispute.created} when the customer
 * disputes. We move the booking to {@code cancelled} with reason {@code chargeback} and surface
 * the dispute to operations through standard logging — finance handles the rest in Stripe.
 */
@Component
public class ChargebackHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ChargebackHandler.class);

  private final PaymentEventRepository events;
  private final BookingRepository bookings;
  private final BookingStateMachine stateMachine;
  private final Clock clock;
  private final ObjectMapper json;

  public ChargebackHandler(
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
    if (!"charge.dispute.created".equals(event.eventType())) return;

    PaymentEvent row = events.findById(event.paymentEventId()).orElse(null);
    if (row == null || row.getProcessedAt() != null) return;

    try {
      JsonNode payload = json.readTree(row.getPayload());
      JsonNode dispute = payload.path("data").path("object");
      String paymentIntentId = dispute.path("payment_intent").asText(null);

      if (paymentIntentId == null || paymentIntentId.isBlank()) {
        LOG.warn("dispute without payment_intent; marking processed");
        markProcessed(row);
        return;
      }

      Optional<Booking> match = bookings.findByStripePaymentIntentId(paymentIntentId);
      if (match.isEmpty()) {
        LOG.warn("dispute for unknown payment_intent {}; marking processed", paymentIntentId);
        markProcessed(row);
        return;
      }

      Booking b = match.get();
      if (b.getStatus() == BookingStatus.cancelled) {
        markProcessed(row);
        return;
      }
      stateMachine.transition(b.getId(), b.getStatus(), BookingStatus.cancelled, CancellationReason.chargeback);
      LOG.warn("booking {} cancelled via chargeback (from {}, dispute pi={})",
          b.getId(), b.getStatus(), paymentIntentId);
      markProcessed(row);

    } catch (Exception e) {
      LOG.error("failed to process dispute event {}", event.paymentEventId(), e);
      row.setProcessingError(e.getMessage());
      events.save(row);
    }
  }

  private void markProcessed(PaymentEvent row) {
    row.setProcessedAt(Instant.now(clock));
    events.saveAndFlush(row);
  }
}
