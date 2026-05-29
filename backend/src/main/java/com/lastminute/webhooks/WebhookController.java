package com.lastminute.webhooks;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spec §5 Flow 1 step 7 + §6.1: thin webhook intake. Verifies signature, persists to
 * {@code payment_events} (UNIQUE on {@code stripe_event_id} → idempotent), publishes a Spring
 * application event for downstream {@code @TransactionalEventListener} handlers. On publish
 * failure, writes to the {@code webhook_dead_letter} table and still returns 200 so Stripe doesn't
 * spam retries — the drain job (Task 11) re-publishes from DLQ.
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

  private static final Logger LOG = LoggerFactory.getLogger(WebhookController.class);

  private final PaymentEventRepository events;
  private final WebhookDeadLetterRepository deadLetter;
  private final ApplicationEventPublisher publisher;
  private final String webhookSecret;

  public WebhookController(
      PaymentEventRepository events,
      WebhookDeadLetterRepository deadLetter,
      ApplicationEventPublisher publisher,
      @Value("${app.stripe.webhook-secret:}") String webhookSecret) {
    this.events = events;
    this.deadLetter = deadLetter;
    this.publisher = publisher;
    this.webhookSecret = webhookSecret;
  }

  @PostMapping("/stripe")
  @Transactional
  public ResponseEntity<Void> stripe(
      @RequestHeader("Stripe-Signature") String signature, @RequestBody String rawBody) {

    Event event;
    try {
      event = Webhook.constructEvent(rawBody, signature, webhookSecret);
    } catch (SignatureVerificationException e) {
      LOG.warn("rejected webhook with bad signature");
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    // Fast-path idempotency: if we've already persisted this event id, return 200 immediately.
    // Without this check the DataIntegrityViolation only fires at commit time (defer-flush),
    // which leaks out past the controller's @Transactional boundary.
    if (events.findByStripeEventId(event.getId()).isPresent()) {
      LOG.debug("duplicate webhook {} (idempotency working)", event.getId());
      return ResponseEntity.ok().build();
    }

    PaymentEvent saved;
    try {
      PaymentEvent pe = new PaymentEvent();
      pe.setStripeEventId(event.getId());
      pe.setEventType(event.getType());
      pe.setPayload(rawBody);
      saved = events.saveAndFlush(pe);
    } catch (DataIntegrityViolationException dup) {
      // Race window between findByStripeEventId and saveAndFlush — second writer loses
      // and we treat it as already-processed.
      LOG.debug("duplicate webhook {} (race resolved by UNIQUE constraint)", event.getId());
      return ResponseEntity.ok().build();
    }

    try {
      publisher.publishEvent(new StripeEventReceived(saved.getId(), event.getType()));
    } catch (Exception publishFail) {
      LOG.error("publish failed for event {}, routing to DLQ", event.getId(), publishFail);
      WebhookDeadLetter dl = new WebhookDeadLetter();
      dl.setStripeEventId(event.getId());
      dl.setEventType(event.getType());
      dl.setPayload(rawBody);
      dl.setLastError(publishFail.getMessage());
      deadLetter.save(dl);
    }
    return ResponseEntity.ok().build();
  }
}
