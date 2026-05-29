package com.lastminute.webhooks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spec §6.1 webhook DLQ. Every 30 seconds, re-publish the next batch of failed events. Caps at
 * 20 retries per row to avoid infinite loops on poisoned messages.
 */
@Component
public class DeadLetterDrainJob {

  static final int MAX_BATCH = 100;
  static final int RETRY_CAP = 20;

  private static final Logger LOG = LoggerFactory.getLogger(DeadLetterDrainJob.class);

  private final WebhookDeadLetterRepository deadLetter;
  private final PaymentEventRepository events;
  private final ApplicationEventPublisher publisher;

  public DeadLetterDrainJob(
      WebhookDeadLetterRepository deadLetter,
      PaymentEventRepository events,
      ApplicationEventPublisher publisher) {
    this.deadLetter = deadLetter;
    this.events = events;
    this.publisher = publisher;
  }

  @Scheduled(fixedRate = 30_000L)
  public void drain() {
    drainNow();
  }

  /** Exposed for tests — same body as the scheduled job, callable on demand. */
  @Transactional
  public int drainNow() {
    var batch = deadLetter.findAllByOrderByFirstFailedAtAsc(Limit.of(MAX_BATCH));
    if (batch.isEmpty()) return 0;

    int delivered = 0;
    for (WebhookDeadLetter dl : batch) {
      // Look up the payment_events row for this stripe_event_id so the handler can find it.
      var pe = events.findByStripeEventId(dl.getStripeEventId());
      if (pe.isEmpty()) {
        // payment_events row missing — recreate so handlers have something to process.
        PaymentEvent recovered = new PaymentEvent();
        recovered.setStripeEventId(dl.getStripeEventId());
        recovered.setEventType(dl.getEventType());
        recovered.setPayload(dl.getPayload());
        pe = java.util.Optional.of(events.save(recovered));
      }

      try {
        publisher.publishEvent(new StripeEventReceived(pe.get().getId(), dl.getEventType()));
        deadLetter.delete(dl);
        delivered++;
      } catch (Exception e) {
        int retries = dl.getRetries() + 1;
        dl.setRetries(retries);
        dl.setLastError(e.getMessage());
        if (retries >= RETRY_CAP) {
          LOG.error(
              "DLQ row {} hit retry cap of {}; leaving in place for manual triage",
              dl.getStripeEventId(),
              RETRY_CAP);
        }
        deadLetter.save(dl);
      }
    }

    if (delivered > 0) LOG.info("DLQ drained {} of {} rows", delivered, batch.size());
    return delivered;
  }
}
