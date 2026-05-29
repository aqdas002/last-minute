package com.lastminute.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lastminute.auth.ResendClient;
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
 * Spec §5 Flow 2 step 6 + §6.1: when Stripe sends {@code account.updated}, mirror the relevant
 * flags onto our provider row. If both charges and payouts become enabled AND the provider is
 * still {@code pending_kyc}, flip them to {@code active} and email "you're live."
 *
 * <p>Idempotent: re-running on an already-processed event row is a no-op (checked via
 * {@code processed_at IS NULL}).
 */
@Component
public class AccountUpdatedHandler {

  private static final Logger LOG = LoggerFactory.getLogger(AccountUpdatedHandler.class);

  private final PaymentEventRepository events;
  private final ProviderRepository providers;
  private final ResendClient email;
  private final Clock clock;
  private final ObjectMapper json;

  public AccountUpdatedHandler(
      PaymentEventRepository events,
      ProviderRepository providers,
      ResendClient email,
      Clock clock,
      ObjectMapper json) {
    this.events = events;
    this.providers = providers;
    this.email = email;
    this.clock = clock;
    this.json = json;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onEvent(StripeEventReceived event) {
    if (!"account.updated".equals(event.eventType())) return;

    PaymentEvent row =
        events.findById(event.paymentEventId()).orElse(null);
    if (row == null || row.getProcessedAt() != null) {
      LOG.debug("skipping already-processed event {}", event.paymentEventId());
      return;
    }

    try {
      JsonNode payload = json.readTree(row.getPayload());
      JsonNode account = payload.path("data").path("object");
      String accountId = account.path("id").asText();
      boolean chargesEnabled = account.path("charges_enabled").asBoolean(false);
      boolean payoutsEnabled = account.path("payouts_enabled").asBoolean(false);

      Optional<Provider> match = providers.findByStripeAccountId(accountId);
      if (match.isEmpty()) {
        LOG.warn("account.updated for unknown account {} (no provider row); marking processed", accountId);
        row.setProcessedAt(Instant.now(clock));
        events.save(row);
        return;
      }

      Provider p = match.get();
      p.setStripeChargesEnabled(chargesEnabled);
      p.setStripePayoutsEnabled(payoutsEnabled);

      if (chargesEnabled && payoutsEnabled && p.getStatus() == ProviderStatus.pending_kyc) {
        p.setStatus(ProviderStatus.active);
        email.sendProviderLive(emailOf(p));
        LOG.info("provider {} is now active", p.getId());
      }
      providers.saveAndFlush(p);

      row.setProcessedAt(Instant.now(clock));
      events.saveAndFlush(row);

    } catch (Exception e) {
      LOG.error("failed to process account.updated", e);
      row.setProcessingError(e.getMessage());
      events.save(row);
    }
  }

  /** Best-effort email lookup — falls back if the user join isn't loaded. */
  private String emailOf(Provider p) {
    return p.getUser() != null ? p.getUser().getEmail() : "unknown";
  }
}
