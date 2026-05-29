package com.lastminute.bookings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lastminute.webhooks.PaymentEvent;
import com.lastminute.webhooks.PaymentEventRepository;
import com.lastminute.webhooks.StripeEventReceived;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Spec §5 Flow 1 step 8: webhook → confirm. Listens for {@code checkout.session.completed}
 * events; flips the matching booking from {@code pending → confirmed}. Idempotent: replay
 * (DLQ drain, Stripe webhook retry) is a no-op via the state machine's
 * {@code alreadyApplied} path.
 *
 * <p>{@code AFTER_COMMIT} + {@code REQUIRES_NEW} per the M2 retro pattern.
 */
@Component
public class BookingConfirmedHandler {

  private static final Logger LOG = LoggerFactory.getLogger(BookingConfirmedHandler.class);

  private final PaymentEventRepository events;
  private final BookingRepository bookings;
  private final BookingStateMachine stateMachine;
  private final Clock clock;
  private final ObjectMapper json;

  public BookingConfirmedHandler(
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
    if (!"checkout.session.completed".equals(event.eventType())) return;

    PaymentEvent row = events.findById(event.paymentEventId()).orElse(null);
    if (row == null || row.getProcessedAt() != null) return;

    try {
      JsonNode payload = json.readTree(row.getPayload());
      JsonNode session = payload.path("data").path("object");
      String sessionId = session.path("id").asText();
      String bookingIdStr = session.path("metadata").path("booking_id").asText("");
      String paymentIntentId = session.path("payment_intent").asText(null);

      if (bookingIdStr.isBlank()) {
        LOG.warn("checkout.session.completed without booking_id metadata; marking processed");
        markProcessed(row);
        return;
      }

      UUID bookingId = UUID.fromString(bookingIdStr);
      Optional<Booking> match = bookings.findById(bookingId);
      if (match.isEmpty()) {
        LOG.warn("checkout.session.completed for unknown booking {}; marking processed", bookingId);
        markProcessed(row);
        return;
      }

      Booking b = match.get();
      // Validate the session id matches what we recorded (defends against spoofed metadata).
      if (b.getStripeCheckoutSessionId() != null && !b.getStripeCheckoutSessionId().equals(sessionId)) {
        LOG.error("session id mismatch for booking {}: stored={} event={}",
            bookingId, b.getStripeCheckoutSessionId(), sessionId);
        row.setProcessingError("session_id_mismatch");
        events.save(row);
        return;
      }

      if (paymentIntentId != null) {
        b.setStripePaymentIntentId(paymentIntentId);
        bookings.save(b);
      }

      var result =
          stateMachine.transition(bookingId, BookingStatus.pending, BookingStatus.confirmed, null);
      if (result.alreadyApplied()) {
        LOG.debug("booking {} already confirmed (replay)", bookingId);
      } else {
        LOG.info("booking {} confirmed", bookingId);
      }
      markProcessed(row);

    } catch (Exception e) {
      LOG.error("failed to confirm booking from event {}", event.paymentEventId(), e);
      row.setProcessingError(e.getMessage());
      events.save(row);
    }
  }

  private void markProcessed(PaymentEvent row) {
    row.setProcessedAt(Instant.now(clock));
    events.saveAndFlush(row);
  }
}
