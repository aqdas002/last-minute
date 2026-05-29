# M3 — Consumer Booking + Payment Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development or superpowers:executing-plans to implement task-by-task.
>
> **The riskiest milestone in the project.** Concurrency, webhook idempotency, the in-flight Stripe Checkout race, and the consumer-visible "Processing — usually under an hour" UX all converge here.

**Goal:** End-to-end paid booking. Consumer taps Book, pays via Stripe Checkout (hosted), redirects back with a confirmed booking + redemption code. Edge cases that bite real marketplaces — double-tap, sold-out races, abandoned checkouts, delayed webhooks — all handled.

**Builds on M2:** webhook intake (`/api/webhooks/stripe`), application-event handler pattern, DLQ drain job. Reuses `PricingService`, `ClockConfig`, the eager-load DTO trick.

**Canonical refs:**
- Spec: `docs/superpowers/specs/2026-05-26-last-minute-booking-design.md` §3.4 (state machine), §4 (bookings table), §5 Flow 1 (booking), §6.1–6.3 (errors).
- M2 retro: `docs/superpowers/retros/2026-05-29-m2-retro.md`

## Prerequisites

- M2 green on `main`.
- Stripe test keys set: `STRIPE_SECRET_KEY=sk_test_…`, `STRIPE_WEBHOOK_SECRET=whsec_…`. For local dev: `stripe listen --forward-to localhost:8080/api/webhooks/stripe` and use its printed secret.

---

## Task 1 — Flyway V4: `bookings` table + indices + state-machine CHECK

`backend/src/main/resources/db/migration/V4__bookings.sql`:

```sql
CREATE TYPE booking_status AS ENUM ('pending','confirmed','cancelled','completed','no_show');
CREATE TYPE cancellation_reason AS ENUM (
  'consumer_no_pay','provider_cancelled','provider_no_show','refund','chargeback','system');

CREATE TABLE bookings (
  id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  listing_id                  UUID NOT NULL REFERENCES listings(id) ON DELETE RESTRICT,
  consumer_id                 UUID NOT NULL REFERENCES users(id)    ON DELETE RESTRICT,
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
  refresh_count               INT NOT NULL DEFAULT 0,
  refresh_last_at             TIMESTAMPTZ,
  created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Concurrency keys
CREATE UNIQUE INDEX bookings_active_per_consumer
  ON bookings (listing_id, consumer_id)
  WHERE status IN ('pending','confirmed');

CREATE UNIQUE INDEX bookings_active_code_per_provider
  ON bookings (provider_id, redemption_code)
  WHERE status IN ('pending','confirmed');

CREATE INDEX bookings_consumer_recent ON bookings (consumer_id, created_at DESC);
CREATE INDEX bookings_pending_ttl ON bookings (pending_expires_at) WHERE status = 'pending';
CREATE INDEX bookings_checkout_session ON bookings (stripe_checkout_session_id);
```

Test: full suite still green after V4 (no entity changes yet).

## Task 2 — `Booking` JPA entity + repository

Entity per spec §4. Status + cancellation_reason via `@JdbcType(PostgreSQLEnumJdbcType.class)`. Currency via `@JdbcType(SqlTypes.CHAR)`. `payment_events.booking_id` becomes a meaningful FK from M2 onward (no schema change needed; just start populating it from M3).

Repository methods (passed as `@Param`s to avoid JPQL enum-cast issues):
- `findActiveForConsumer(listingId, consumerId, statuses) → Optional<Booking>`
- `countByListingIdAndStatusIn(listingId, statuses) → long`  *(used by tightened material-edit predicate)*
- `findByStripeCheckoutSessionId(id) → Optional<Booking>`
- `findActiveByCodeAndProviderId(code, providerId) → Optional<Booking>`
- `findExpiredPendingOlderThan(instant) → List<Booking>` *(for sweeper)*

## Task 3 — `BookingStateMachine` service

Encodes spec §3.4 transitions in code. Method: `transition(bookingId, from, to, reason?)`:
- `UPDATE bookings SET status=$to, cancellation_reason=$reason, ...timestamp WHERE id=$id AND status=$from`
- 0-row update → look up current status; if it's already `$to`, return `{alreadyApplied: true}` (idempotent retry); else throw `IllegalTransitionError`.

Tests cover every allowed and disallowed transition.

## Task 4 — `ReserveSpotService`

Spec §5 Flow 1 steps 3–4b, the core booking transaction. Single `@Transactional` method `reserveSpot(listingId, consumerId)`:

1. `SELECT * FROM listings WHERE id=$1 FOR UPDATE` via `@Query("SELECT l FROM Listing l WHERE l.id = :id") @Lock(LockModeType.PESSIMISTIC_WRITE)` on a repository finder.
2. Check `status='active' AND listing_expires_at > clock.now()` → throw `LISTING_EXPIRED` / `SOLD_OUT`-style typed errors.
3. Fast-path: `bookings.findActiveForConsumer(listingId, consumerId, ['pending','confirmed'])` → if present, return existing (handles double-tap deterministically; matches M2 webhook idempotency pattern).
4. `bookings.countByListingIdAndStatusIn(listingId, ['pending','confirmed']) >= capacity` → throw `SOLD_OUT`.
5. `INSERT bookings(...)` with status=pending, fee + payout from `PricingService`, `pending_expires_at = clock.now() + 35 min`, `redemption_code = randomCode()` (8-char alphanumeric, no ambiguous chars).
6. Return the saved booking.

Concurrency IT: `Promise.all` × N starts on a `capacity=1` listing → exactly 1 succeeds, others throw `SOLD_OUT`. Use `Awaitility` + a `ThreadPoolExecutor` to run them in parallel.

## Task 5 — `BookingController` — POST /api/bookings + GET /api/bookings/{id}

Endpoints:
- `POST /api/bookings` body `{listingId}` (authenticated, any role):
  1. Call `ReserveSpotService.reserveSpot(...)` (inside its own tx, commits).
  2. After commit, call `stripeService.createCheckoutSession(booking)` with `idempotencyKey: booking.id`, `expires_at: now + 30min`, `application_fee_amount`, `transfer_data.destination`.
  3. On Stripe API failure: `bookingStateMachine.transition(booking.id, pending, cancelled, 'system')`; return 503.
  4. On success: `UPDATE bookings SET stripe_checkout_session_id=... WHERE id=... AND status='pending' RETURNING id`. If 0 rows (sweeper killed it during the Stripe call) → `stripe.checkoutSessions.expire(session.id)` + return 409 with retry guidance.
  5. Return `{bookingId, checkoutUrl}`.
- `GET /api/bookings/{id}` — only the consumer who owns it (or admin). Returns the booking DTO with status + redemption code (only if confirmed).
- `GET /api/bookings/me` — list mine, newest first.
- `POST /api/bookings/{id}/refresh-status` — on-demand Stripe session retrieve + state reconcile. Rate-limited: max 1 per 10 s, max 6 per hour per booking (using `refresh_count` + `refresh_last_at` on the bookings row).

## Task 6 — `BookingConfirmedHandler` (`@TransactionalEventListener` on `StripeEventReceived`)

Listens for `checkout.session.completed` events. Flow:
1. Parse `session.metadata.booking_id`.
2. `bookingStateMachine.transition(id, pending, confirmed, null)` — set `confirmed_at`, persist `stripe_payment_intent_id` from event.
3. Mark `payment_events.processed_at`.
4. Emit Spring `BookingConfirmed` application event for the email scheduler.

`@Transactional(propagation = REQUIRES_NEW)` per the M2 retro pattern.

Out-of-order edge cases:
- `checkout.session.completed` arrives after `pending_expires_at` (sweeper already cancelled) → call `stripe.refunds.create` + log; state stays `cancelled` with reason `consumer_no_pay`.
- `payment_intent.succeeded` is a no-op (we treat `checkout.session.completed` as the source of truth).
- Duplicate `checkout.session.completed` → idempotent via `transition`'s `alreadyApplied` path.

## Task 7 — Scheduled jobs

- `PendingBookingSweeper` — `@Scheduled(cron = "0 * * * * *")` (every minute). Finds bookings where `status='pending' AND pending_expires_at < clock.now()`, transitions each to `cancelled` with reason `consumer_no_pay`.
- `ReminderEmailJob` — `@Scheduled(fixedRate = 60_000)`. Finds confirmed bookings with `start_time BETWEEN now AND now+1h AND reminder_sent_at IS NULL`, sends "starts in 1 hour" email via `ResendClient.sendBookingReminder`, marks `reminder_sent_at`.
- Add `reminder_sent_at TIMESTAMPTZ` to bookings in a follow-up Flyway V5.

## Task 8 — Wire the tightened predicates (M2 → M3 follow-ups)

- `ProviderListingService.hasActiveBookings(listingId)` — swap M2's `return false` for `bookingRepo.countByListingIdAndStatusIn(listingId, [pending, confirmed]) > 0`.
- `ProviderSettingsController.changeCurrency` — replace M2's "any listing exists" check with "any booking exists for any of my listings" (`bookingRepo.existsByProviderId(providerId)`).

Update the M2 tests that asserted "no bookings → allowed" so they still pass; add new tests "with a booking → blocked".

## Task 9 — Frontend: Book button + success page + refresh

- `src/api/bookings.ts`: typed clients.
- `src/pages/listing.tsx`: replace "Booking enabled in next milestone" with a sign-in-gated Book button. On click, POST `/api/bookings`, `window.location.assign(checkoutUrl)`.
- `src/pages/booking-success.tsx`: route `/bookings/:id/success`. Polls the booking every 1s × 30s. If still `pending` after timeout, switch to "Processing — usually under an hour. We'll email you the confirmation." with a "Refresh status" button calling the rate-limited endpoint.
- `src/pages/my-bookings.tsx`: route `/bookings`. List with status pill + redemption code (when confirmed).

## Task 10 — End-to-end manual smoke (needs Stripe test keys)

1. `stripe listen --forward-to localhost:8080/api/webhooks/stripe` in one terminal.
2. Backend with `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `SPRING_PROFILES_ACTIVE=dev-seed`.
3. Frontend `pnpm dev`.
4. Sign in as a non-admin user. Open the seeded listing. Click Book.
5. Pay with `4242 4242 4242 4242` on Stripe Checkout. Redirect back.
6. Confirm success page shows confirmed state within ~2s. Redemption code visible.
7. Force concurrency by booking from two browser sessions on a `capacity=1` listing — only one wins.

## Acceptance

| # | Criterion | Tasks |
|---|---|---|
| 1 | One full paid booking on test mode end-to-end | 4, 5, 6, 9, 10 |
| 2 | N=10 concurrent Book on capacity=1 → exactly 1 confirmed | 4 |
| 3 | Double-tap Book returns same pending booking (no opaque 23505) | 4 |
| 4 | Sweeper kills abandoned pending within 60 s | 7 |
| 5 | Material-edit blocked when pending booking exists; allowed when not | 8 |
| 6 | Refresh-status button is rate-limited | 5 |

**Estimate:** ~4 person-days. The concurrency tests and the step-4b race recovery will eat most of the surprise time.
