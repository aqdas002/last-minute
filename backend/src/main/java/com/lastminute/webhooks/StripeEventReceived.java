package com.lastminute.webhooks;

import java.util.UUID;

/**
 * Spring application event published by {@link WebhookController} after persisting an incoming
 * Stripe webhook to {@code payment_events}. Handlers in other packages listen via
 * {@code @TransactionalEventListener} so they only run after the insert commits.
 */
public record StripeEventReceived(UUID paymentEventId, String eventType) {}
