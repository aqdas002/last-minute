package com.lastminute.providers;

import static org.assertj.core.api.Assertions.assertThat;

import com.lastminute.support.IntegrationTestBase;
import com.lastminute.users.User;
import com.lastminute.users.UserRepository;
import com.lastminute.users.UserRole;
import com.lastminute.webhooks.PaymentEvent;
import com.lastminute.webhooks.PaymentEventRepository;
import com.lastminute.webhooks.StripeEventReceived;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

class AccountUpdatedHandlerIT extends IntegrationTestBase {

  @Autowired private PaymentEventRepository events;
  @Autowired private ProviderRepository providers;
  @Autowired private UserRepository users;
  @Autowired private ApplicationEventPublisher publisher;
  @Autowired private TransactionTemplate tx;

  private Provider seedProvider(String email, String stripeAccountId) {
    User u = new User();
    u.setEmail(email);
    u.setRole(UserRole.provider);
    u = users.save(u);

    Provider p = new Provider();
    p.setId(u.getId());
    p.setBusinessName("Biz " + email);
    p.setCurrency("USD");
    p.setTimezone("America/New_York");
    p.setCountry("US");
    p.setStripeAccountId(stripeAccountId);
    p.setStatus(ProviderStatus.pending_kyc);
    return providers.save(p);
  }

  private PaymentEvent seedAccountUpdatedEvent(
      String stripeAccountId, boolean chargesEnabled, boolean payoutsEnabled) {
    String payload =
        """
        {"id":"evt_x","type":"account.updated","data":{"object":{
          "id":"%s","object":"account",
          "charges_enabled":%s,"payouts_enabled":%s}}}
        """.formatted(stripeAccountId, chargesEnabled, payoutsEnabled);
    PaymentEvent pe = new PaymentEvent();
    pe.setStripeEventId("evt_" + stripeAccountId);
    pe.setEventType("account.updated");
    pe.setPayload(payload);
    return events.save(pe);
  }

  /** Publishing inside a transaction is what {@link WebhookController} does; mirror that here. */
  private void publishInTx(PaymentEvent pe) {
    tx.executeWithoutResult(
        s -> publisher.publishEvent(new StripeEventReceived(pe.getId(), pe.getEventType())));
  }

  @Test
  void both_enabled_flips_status_to_active() {
    Provider p = seedProvider("flip@example.com", "acct_FLIP");
    PaymentEvent pe = seedAccountUpdatedEvent("acct_FLIP", true, true);

    publishInTx(pe);

    Provider after = providers.findById(p.getId()).orElseThrow();
    assertThat(after.isStripeChargesEnabled()).isTrue();
    assertThat(after.isStripePayoutsEnabled()).isTrue();
    assertThat(after.getStatus()).isEqualTo(ProviderStatus.active);

    PaymentEvent processed = events.findById(pe.getId()).orElseThrow();
    assertThat(processed.getProcessedAt()).isNotNull();
  }

  @Test
  void charges_disabled_keeps_status_pending() {
    Provider p = seedProvider("nope@example.com", "acct_NOPE");
    PaymentEvent pe = seedAccountUpdatedEvent("acct_NOPE", false, true);

    publishInTx(pe);

    Provider after = providers.findById(p.getId()).orElseThrow();
    assertThat(after.isStripeChargesEnabled()).isFalse();
    assertThat(after.isStripePayoutsEnabled()).isTrue();
    assertThat(after.getStatus()).isEqualTo(ProviderStatus.pending_kyc);
  }

  @Test
  void unknown_account_id_marks_event_processed_without_throwing() {
    PaymentEvent pe = seedAccountUpdatedEvent("acct_UNKNOWN", true, true);

    publishInTx(pe);

    PaymentEvent processed = events.findById(pe.getId()).orElseThrow();
    assertThat(processed.getProcessedAt()).isNotNull();
  }

  @Test
  void replay_on_already_processed_event_is_a_noop() {
    Provider p = seedProvider("idem@example.com", "acct_IDEM");
    PaymentEvent pe = seedAccountUpdatedEvent("acct_IDEM", true, true);

    publishInTx(pe);
    Provider afterFirst = providers.findById(p.getId()).orElseThrow();
    assertThat(afterFirst.getStatus()).isEqualTo(ProviderStatus.active);

    // Now simulate a stale enqueue (e.g., DLQ replay).
    publishInTx(pe);

    Provider afterSecond = providers.findById(p.getId()).orElseThrow();
    assertThat(afterSecond.getStatus()).isEqualTo(ProviderStatus.active); // unchanged
  }
}
