CREATE TYPE booking_status AS ENUM ('pending', 'confirmed', 'cancelled', 'completed', 'no_show');
CREATE TYPE cancellation_reason AS ENUM (
  'consumer_no_pay', 'provider_cancelled', 'provider_no_show', 'refund', 'chargeback', 'system');

CREATE TABLE bookings (
  id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  listing_id                  UUID NOT NULL REFERENCES listings(id)  ON DELETE RESTRICT,
  consumer_id                 UUID NOT NULL REFERENCES users(id)     ON DELETE RESTRICT,
  provider_id                 UUID NOT NULL REFERENCES providers(id) ON DELETE RESTRICT,
  status                      booking_status NOT NULL DEFAULT 'pending',
  amount_paid_cents           INT NOT NULL,
  platform_fee_cents          INT NOT NULL,
  provider_payout_cents       INT NOT NULL,
  currency                    CHAR(3) NOT NULL,
  stripe_checkout_session_id  TEXT UNIQUE,
  stripe_payment_intent_id    TEXT,
  pending_expires_at          TIMESTAMPTZ NOT NULL,
  confirmed_at                TIMESTAMPTZ,
  cancelled_at                TIMESTAMPTZ,
  cancellation_reason         cancellation_reason,
  redemption_code             CHAR(8) NOT NULL,
  redeemed_at                 TIMESTAMPTZ,
  reminder_sent_at            TIMESTAMPTZ,
  refresh_count               INT NOT NULL DEFAULT 0,
  refresh_last_at             TIMESTAMPTZ,
  created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Spec §4: at most one active booking per (listing, consumer); cancel-then-rebook permitted.
CREATE UNIQUE INDEX bookings_active_per_consumer
  ON bookings (listing_id, consumer_id)
  WHERE status IN ('pending', 'confirmed');

-- Spec §4: per-provider redemption code uniqueness among active bookings.
CREATE UNIQUE INDEX bookings_active_code_per_provider
  ON bookings (provider_id, redemption_code)
  WHERE status IN ('pending', 'confirmed');

CREATE INDEX bookings_consumer_recent  ON bookings (consumer_id, created_at DESC);
CREATE INDEX bookings_pending_ttl      ON bookings (pending_expires_at) WHERE status = 'pending';
CREATE INDEX bookings_checkout_session ON bookings (stripe_checkout_session_id);
-- Reminder sweeper: scans confirmed bookings with reminder_sent_at IS NULL, joined to listings.
CREATE INDEX bookings_reminder_pending ON bookings (reminder_sent_at) WHERE status = 'confirmed';
