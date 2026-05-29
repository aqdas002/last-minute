-- Consumer-initiated refund requests. Spec §6.4 promise: "Full refund if the provider
-- doesn't honor your booking." Backed by an admin-reviewed queue (manual Stripe refund
-- via the Dashboard for the MVP — the resulting charge.refunded webhook flows through
-- RefundHandler).

CREATE TYPE refund_request_reason AS ENUM (
  'provider_no_show',     -- Provider was a no-show / closed / not honoring the deal
  'quality_issue',        -- Honored the booking but the experience was misrepresented
  'duplicate_charge',     -- Consumer believes they were charged twice
  'other'                 -- Free-text reason; admin reads details
);

CREATE TYPE refund_request_status AS ENUM (
  'open',                 -- Awaiting admin review
  'approved',             -- Admin approved; refund expected via Stripe
  'denied',               -- Admin denied; reason logged
  'auto_resolved'         -- A charge.refunded webhook arrived during open; consider closed
);

CREATE TABLE refund_requests (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id      UUID NOT NULL REFERENCES bookings(id) ON DELETE RESTRICT,
  consumer_id     UUID NOT NULL REFERENCES users(id)    ON DELETE RESTRICT,
  reason          refund_request_reason NOT NULL,
  details         TEXT,
  status          refund_request_status NOT NULL DEFAULT 'open',
  admin_notes     TEXT,
  resolved_at     TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Spec §6.4: one open request per booking at a time. Filing again is permitted only after
-- the prior one resolved (denied or auto_resolved).
CREATE UNIQUE INDEX refund_requests_one_open_per_booking
  ON refund_requests (booking_id)
  WHERE status = 'open';

CREATE INDEX refund_requests_status_recent
  ON refund_requests (status, created_at DESC);

CREATE INDEX refund_requests_consumer_recent
  ON refund_requests (consumer_id, created_at DESC);
