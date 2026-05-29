package com.lastminute.webhooks;

import static org.assertj.core.api.Assertions.assertThat;

import com.lastminute.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DeadLetterDrainJobIT extends IntegrationTestBase {

  @Autowired private DeadLetterDrainJob job;
  @Autowired private WebhookDeadLetterRepository deadLetter;
  @Autowired private PaymentEventRepository events;

  @Test
  void drain_processes_pending_rows_and_deletes_on_success() {
    WebhookDeadLetter dl = new WebhookDeadLetter();
    dl.setStripeEventId("evt_DLQ1");
    dl.setEventType("account.updated");
    dl.setPayload("""
        {"id":"evt_DLQ1","type":"account.updated","data":{"object":{
          "id":"acct_UNKNOWN_NOOP","charges_enabled":false,"payouts_enabled":false}}}
        """);
    deadLetter.save(dl);

    int delivered = job.drainNow();

    assertThat(delivered).isEqualTo(1);
    assertThat(deadLetter.count()).isEqualTo(0);
    // The drain re-creates a payment_events row so handlers have something to process.
    assertThat(events.findByStripeEventId("evt_DLQ1")).isPresent();
  }

  @Test
  void empty_dlq_returns_zero() {
    assertThat(job.drainNow()).isEqualTo(0);
  }
}
