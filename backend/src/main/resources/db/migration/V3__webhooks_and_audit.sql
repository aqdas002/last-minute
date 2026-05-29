-- Webhook event log (idempotency + audit). Per spec §4.
CREATE TABLE payment_events (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id       UUID,                                          -- nullable: events arriving without booking context
  event_type       TEXT NOT NULL,
  stripe_event_id  TEXT NOT NULL UNIQUE,
  payload          JSONB NOT NULL,
  received_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_at     TIMESTAMPTZ,
  processing_error TEXT
);
CREATE INDEX payment_events_booking_event_idx ON payment_events (booking_id, event_type);
CREATE INDEX payment_events_unprocessed_idx ON payment_events (processed_at) WHERE processed_at IS NULL;

-- DLQ for webhook delivery failures (Inngest replacement; drained by a @Scheduled job).
CREATE TABLE webhook_dead_letter (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  stripe_event_id  TEXT NOT NULL UNIQUE,
  event_type       TEXT NOT NULL,
  payload          JSONB NOT NULL,
  first_failed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  retries          INT NOT NULL DEFAULT 0,
  last_error       TEXT
);

-- Admin audit log (currency override, listing force-cancel, etc.).
CREATE TABLE admin_actions (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_user_id    UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  action           TEXT NOT NULL,             -- e.g. "provider.change_currency"
  target_id        UUID,
  reason           TEXT NOT NULL,
  payload          JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX admin_actions_actor_idx ON admin_actions (actor_user_id, created_at DESC);
