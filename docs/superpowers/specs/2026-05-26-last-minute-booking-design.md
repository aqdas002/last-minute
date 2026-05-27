# Last-Minute Booking Marketplace — Design Spec

**Status:** Draft for implementation
**Date:** 2026-05-26
**Scope:** v1 (MVP for paying users), single-city pilot

---

## 1. Product summary

A two-sided web marketplace where providers list perishable last-minute inventory at a discount and consumers browse and book it. Generic across categories (hotels, restaurants, fitness, services, events). Consumer pays through the platform; platform takes a 15% commission via Stripe Connect.

### Locked-in product decisions

| Decision | Choice |
|---|---|
| Inventory model | Two-sided marketplace; providers list themselves |
| Verticals at launch | Generic across all categories |
| Platforms | Mobile-responsive web; native apps deferred |
| Discount mechanic | Provider sets a fixed discounted price |
| Booking time window | Provider-configurable per listing |
| Discovery | Category-browse-first; ranked by proximity + time-to-start |
| Cancellation policy | Strict non-refundable, all sales final |
| Commission | 15% flat, frozen per booking |
| Payment | Stripe Connect Express, destination charges |
| Geography | Single-city pilot; schema is location-aware to expand |

### Acknowledged product risks (not blockers)

1. **Cold-start with category browse.** PM review flagged that empty category grids on day one will hurt conversion. We accept this in v1 with the mitigations: (a) the default consumer landing surface is a mixed "starting soon near you" feed, *not* the category grid; (b) we hand-seed providers in one chosen category as the soft launch focus.
2. **Strict non-refundable across all categories** carries trust risk in restaurants/services (vs. hotels/fitness where it's accepted). The mitigation is the **provider-no-redeem auto-refund** in §4.
3. **Provider onboarding friction** (Stripe KYC). The provider can prepare draft listings during the `pending_kyc` window; publishing is gated on `stripe_charges_enabled`.

---

## 2. Architecture

```
[Consumers]    [Providers]    [Admin]
     \             |             /
      \            v            /
       [ Next.js 15 App (RSC + Server Actions) ]
       /     |          |        |       \
      v      v          v        v        v
[Postgres  [Stripe   [Auth.js  [Resend  [Sentry
 (Neon)]   Connect   magic+    email]    errors]
            Express]  Google]
      ^
      |
[ Inngest (background jobs + webhook processing) ]
```

**Single Next.js 15 app** owns frontend, API routes, and Server Actions. Deployed on Vercel with previews per PR and Neon DB branching per PR.

### Component responsibilities

- **Next.js app** — UI, server actions, public API routes for webhooks. Runs on Vercel serverless functions.
- **Postgres (Neon)** — source of truth for all business state.
- **Stripe Connect Express** — provider KYC, payments, marketplace splits (`application_fee_amount`), payouts. We are the platform; provider is the connected account.
- **Auth.js (NextAuth v5)** — passwordless email magic links + Google OAuth. HTTP-only session cookie.
- **Resend** — transactional email.
- **Inngest** — scheduled jobs, event-driven jobs, AND **all webhook side-effects** (see §4 webhook architecture).
- **Sentry** — error reporting from server + client.

### Rationale (summary; tradeoffs explored in conversation)

- Monolithic Next.js over separated frontend/backend → solo-MVP velocity, one auth/deploy/repo.
- Server Components + Server Actions over REST/GraphQL → typed RPC for free with single web client.
- Postgres + Prisma over NoSQL/Drizzle → relational data, money/concurrency, geo built-in.
- Stripe Connect Express over Standard/PayPal/escrow → regulatory escape hatch; never touch cards.
- Inngest over plain cron/BullMQ/Temporal → event-precise scheduling without running a worker host.
- Vercel + Neon over self-host → velocity; both portable later.

### Caching

- Server Components fetch via Prisma with `unstable_cache` keyed by `('listings', categoryId, city)` with a short TTL of **15 seconds**. The TTL exists as a safety net only — every write path also invalidates explicitly.
- Writes invalidate by both `revalidateTag('listings:<categoryId>')` AND `revalidatePath('/c/<slug>', 'layout')` — `revalidateTag` alone does not invalidate path-level caches that don't use the same `fetch` tagging.
- Inngest jobs that mutate listings (expiry sweeper, status flips) call the same `invalidateListingsCache(categoryId, citySlug)` helper as Server Actions. The minutely expiry sweeper invalidates *every* category whose listings expired in that tick.
- **All consumer-facing listing queries also include `listing_expires_at > clock.now()` in their WHERE clause** as belt-and-braces. The cache is the optimization; the query predicate is the correctness layer. This means a 15-second stale cache cannot serve an expired listing — the query itself filters it.

---

## 3. Cross-cutting concerns

These concerns cut across every section and are easy to forget.

### 3.1 Time

- **All timestamps are `TIMESTAMPTZ`** (Postgres) / `DateTime @db.Timestamptz(6)` (Prisma).
- **All server code stores and compares in UTC.** Render in local time only at the edges (email templates, UI).
- **`providers.timezone`** and **`listings.timezone`** are IANA strings (e.g. `America/New_York`); listing inherits from provider but can override.
- **"Now" is injectable end-to-end.** A `clock` service is passed to every function that compares to `now()`; tests provide a fixed-time clock. Calling `new Date()` or `Date.now()` inline outside the `clock` service is an ESLint error (allowlist: `lib/clock/**`, test files, generated Prisma client).
- **SQL `now()` is NOT used in business logic.** Any time SQL needs a current timestamp (booking transaction `pending_expires_at`, redemption `redeemed_at`, sweeper comparisons), JS passes the value as a parameter using `clock.now()`. The only acceptable SQL `now()` is in DEFAULT clauses for audit columns (`created_at`, `updated_at`) where exactness doesn't matter. This keeps every time-sensitive code path testable with a frozen clock.
- **DST + "starting soon" filter** runs in UTC; the user sees their local-time relative description ("starts in 2h") rendered client-side from the UTC instant.

### 3.2 Money

- All monetary fields are integer cents (e.g. `amount_paid_cents`) — never floats.
- **Single currency per provider** in v1, declared on `providers.currency` at onboarding and locked. Listings + bookings inherit. Multi-currency is deferred.
- **Commission rounding:** `platform_fee_cents = Math.floor((amount_paid_cents * 15) / 100)` — integer-only math, no floats. Sub-cent residual goes to the platform (floor rounding). Enforced in a single `lib/pricing.ts` function with unit tests for boundary values: `1 → 0`, `7 → 1`, `100 → 15`, `333 → 49`, `999 → 149`, `99999999999 → 14999999999`. The constant `15` (commission percent) lives in one place; do not inline.
- **Currency snapshot.** `bookings.currency` is the snapshot at booking time. If a provider's currency is ever migrated (admin tool), historical bookings keep the original currency.
- **Money columns are frozen on the booking row at creation time** (snapshot). Future commission rate changes don't rewrite history.

### 3.3 Authorization

- **Server Actions re-check session** via `requireSession()` at the top of every action. UI-side redirects are convenience, not security.
- **Every booking and listing read** goes through `requireOwnership(resource, session)` which enforces consumer/provider/admin rules. No "trust the URL" reads.
- **`return_to` parameters** are validated against a narrow allowlist regex, owned by a single function `lib/auth/return-to.ts`. Pattern: `^/(?:\$|c/[a-z0-9-]+(?:\?[^#]*)?$|bookings/[0-9a-f-]+(?:\?[^#]*)?$|book/[0-9a-f-]+$|provider/(?:dashboard|onboarding|bookings|listings)(?:/[a-z0-9-]+)?(?:\?[^#]*)?$)`. Permits an optional query string on the public-facing pages (so "sign in to keep browsing with filter X" works). Falls back to `/` on mismatch. Closes both open-redirect and on-site phishing-landing classes.
- **CSRF:** Auth.js v5's built-in CSRF token covers Server Actions invoked from forms; explicitly verified in an integration test.
- **Admin role:** middleware enforces `role='admin'` on all `/admin/*` routes; not just UI hiding.
- **Redemption** is gated by `listing.provider_id = session.user.providerId` AND `status='confirmed'` AND `redeemed_at IS NULL`. The UPDATE returns row count; zero affected rows is a user-facing error, not silent success.

### 3.4 State machines

- **Booking status transitions are enforced in code by `updateBookingStatus(id, from, to, reason?)`** which performs `UPDATE bookings SET status=$to, cancellation_reason=$reason WHERE id=$id AND status=$from`. Zero affected rows triggers a follow-up `SELECT status FROM bookings WHERE id=$id`:
  - If current status equals `$to` → the transition already happened (likely a retry) → return success with `{alreadyApplied: true}`. Callers treat this as no-op success.
  - If current status is anything else → throw `IllegalTransitionError`. This is critical for handler retries to be idempotent.
- The valid transitions are:
  - `pending → confirmed | cancelled`
  - `confirmed → cancelled | completed | no_show`
  - `completed → cancelled` (allowed for post-redemption refunds and chargebacks; requires `cancellation_reason` of `refund` or `chargeback`)
  - `no_show → cancelled` (allowed for the provider-no-show auto-refund pipeline)
  - `cancelled` is terminal.
- A DB CHECK constraint encodes the enum; the in-code state machine encodes the transition rules. Belt-and-braces.
- **Post-completion refund/chargeback semantics:** A `completed → cancelled` transition does NOT roll back the redemption record (`redeemed_at` stays set). The booking row reflects "service was delivered, but money was returned" — visible in admin tooling and analytics. Reporting queries that count redeemed service-deliveries filter by `redeemed_at IS NOT NULL`, not by status.
- **Provider dashboard rendering rule:** any booking with `redeemed_at IS NOT NULL` AND `status = 'cancelled'` is rendered as a distinct "Refunded after service" pill (NOT a generic "Cancelled") so providers don't think their delivered service vanished. Same rule for `cancellation_reason = 'chargeback'`: render as "Chargeback after service."

---

## 4. Data model

8 tables (7 business + 1 operational DLQ). UUID v7 PKs everywhere. All tables have `created_at TIMESTAMPTZ NOT NULL DEFAULT now()` and `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`.

### `users`
- `id` (uuid v7, PK)
- `email` (TEXT UNIQUE NOT NULL)
- `name`, `phone`
- `role` enum: `consumer | provider | admin`
- `created_at`, `updated_at`

### `providers` (1:1 with users where role=provider)
- `id` (uuid, FK to `users.id` ON DELETE RESTRICT)
- `business_name`, `business_description`, `contact_phone`
- `currency` (CHAR(3), e.g. 'USD', locked at onboarding)
- `timezone` (TEXT, IANA, e.g. 'America/New_York')
- `stripe_account_id` (TEXT UNIQUE)
- `stripe_onboarding_complete` BOOL
- `stripe_charges_enabled` BOOL
- `stripe_payouts_enabled` BOOL
- `default_address`, `default_lat`, `default_lon`, `city`, `country`
- `status` enum: `pending_kyc | active | suspended`
- `created_at`, `updated_at`

### `categories`
- `id` (uuid, PK)
- `slug` (TEXT UNIQUE NOT NULL)
- `name`, `icon_name`
- `parent_id` (nullable FK; v1 stays flat)
- `display_order` INT
- `active` BOOL
- `no_show_grace_interval` INTERVAL NOT NULL DEFAULT '2 hours' — how long after `end_time` before an unredeemed booking auto-refunds. Restaurants seed with 4h; fitness with 1h.
- `created_at`, `updated_at`

### `listings`
- `id` (uuid v7, PK)
- `provider_id` (FK)
- `category_id` (FK)
- `title`, `description`
- `images` (jsonb array of URLs)
- `original_price_cents` INT
- `discounted_price_cents` INT (CHECK `discounted_price_cents < original_price_cents AND discounted_price_cents > 0`)
- `currency` CHAR(3) (denormalized from provider for query speed)
- `capacity` INT NOT NULL DEFAULT 1 (CHECK `capacity >= 1`)
- `start_time` TIMESTAMPTZ
- `end_time` TIMESTAMPTZ (CHECK `end_time > start_time`)
- `listing_expires_at` TIMESTAMPTZ (CHECK `listing_expires_at <= end_time`)
- `timezone` TEXT (inherits from provider; can override)
- `address`, `lat`, `lon`, `city`
- `status` enum: `draft | active | sold_out | expired | cancelled | suspended`
- `metadata` (jsonb)
- `created_at`, `updated_at`
- **Indices:**
  - `(category_id, status, city, listing_expires_at)` — primary consumer feed
  - GIST `(lat, lon)` — nearby search
  - `(provider_id, status)` — provider dashboard

### `bookings`
- `id` (uuid v7, PK)
- `listing_id` (FK ON DELETE RESTRICT)
- `consumer_id` (FK to users ON DELETE RESTRICT)
- `provider_id` (FK to providers ON DELETE RESTRICT — denormalized from listing for the redemption uniqueness index and for the redemption-time query; set explicitly by `reserveSpot()` from the SELECT-FOR-UPDATE'd listing row, NOT via DB trigger; a CI invariant test asserts `bookings.provider_id = listings.provider_id`)
- `status` enum: `pending | confirmed | cancelled | completed | no_show` (CHECK constraint enforces enum)
- `amount_paid_cents`, `platform_fee_cents`, `provider_payout_cents`, `currency` (snapshot, frozen)
- `stripe_checkout_session_id` (TEXT UNIQUE NULLABLE — null while we're between INSERT and Stripe call)
- `stripe_payment_intent_id` (TEXT NULLABLE)
- `pending_expires_at` TIMESTAMPTZ
- Stripe idempotency key on session creation = `booking.id` (no separate column — convention enforced in code)
- `confirmed_at`, `cancelled_at` TIMESTAMPTZ NULLABLE
- `cancellation_reason` enum nullable: `consumer_no_pay | provider_cancelled | provider_no_show | refund | chargeback | system`
- `redemption_code` CHAR(8) NOT NULL (alphanumeric uppercase excluding ambiguous chars: no `0/O/1/I/L`)
- `redeemed_at` TIMESTAMPTZ NULLABLE
- `created_at`, `updated_at`
- **Indices:**
  - **PARTIAL UNIQUE:** `(listing_id, consumer_id) WHERE status IN ('pending','confirmed')`
  - **PARTIAL UNIQUE:** `(provider_id, redemption_code) WHERE status IN ('pending', 'confirmed')` — predicate is on status, not `redeemed_at`. Including redeemed (`completed`) codes in the unique set would forbid reissuing the same string; excluding them via `status` keeps active codes unique while letting `generate_unique_code` recycle terminal codes after retention sweep. Generator still retries on collision.
  - `(consumer_id, created_at DESC)` — consumer's bookings list
  - `(pending_expires_at)` WHERE `status='pending'` — Inngest sweeper
  - `(stripe_checkout_session_id)` — webhook lookup

### `payment_events` (audit log + webhook idempotency)
- `id` (uuid v7, PK)
- `booking_id` (FK NULLABLE — some webhook events arrive without booking context)
- `event_type` TEXT
- `stripe_event_id` (TEXT UNIQUE NOT NULL → idempotency)
- `payload` (jsonb)
- `received_at` TIMESTAMPTZ DEFAULT now()
- `processed_at` TIMESTAMPTZ NULLABLE
- `processing_error` TEXT NULLABLE
- **Indices:** `(booking_id, event_type)` for "is this booking already confirmed?" lookups; `(processed_at) WHERE processed_at IS NULL` for the in-flight queue view.
- **Processing ordering:** Inngest handler writes `processed_at` in the SAME transaction as the downstream booking state mutation. If the handler crashes between booking update and setting `processed_at`, the event is reprocessed on next retry (idempotent because of `updateBookingStatus`'s `WHERE status=$from` guard). Setting `processed_at` BEFORE the booking write would risk losing events.
- **Retention:** Inngest job deletes rows where `received_at < now() - interval '90 days'` AND `processed_at IS NOT NULL`; keeps `stripe_event_id` + `event_type` indefinitely in a thinner `payment_events_archive` table.

### `webhook_dead_letter` (operational queue for Inngest publish failures)
- `id` (uuid v7, PK)
- `stripe_event_id` (TEXT UNIQUE NOT NULL — same idempotency key as `payment_events`)
- `event_type` TEXT
- `payload` jsonb
- `first_failed_at` TIMESTAMPTZ NOT NULL DEFAULT now()
- `retries` INT NOT NULL DEFAULT 0
- `last_error` TEXT NULLABLE
- **Drain job:** Inngest cron every 30s reads up to 100 rows, attempts `inngest.send(...)` for each, deletes on success or increments `retries` + sets `last_error` on failure. After `retries > 20`, alert admin and keep the row (do not infinite-retry).

### Concurrency model

The booking transaction:
```
BEGIN
  SELECT * FROM listings WHERE id=$1 FOR UPDATE;
  -- check status='active', listing_expires_at > $now ($now is passed in from clock.now())
  -- check capacity > active count
  -- if any check fails, ROLLBACK and throw a typed error
  INSERT INTO bookings (..., status='pending', stripe_checkout_session_id=NULL,
                        pending_expires_at = $now + interval '35 minutes',  -- > Stripe's 30-min minimum
                        redemption_code = generate_unique_code(provider_id),
                        created_at = $now, updated_at = $now);
COMMIT;
-- Stripe Checkout Session creation happens AFTER COMMIT (next subsection).
```

The `SELECT FOR UPDATE` serializes only writers to the same listing — readers and writers of other listings fly through.

**Capacity throughput ceiling:** Stripe Checkout Session creation has been moved OUT of the FOR-UPDATE transaction (see Flow 1 below). The hot critical section is now just the DB transaction itself (~5–10ms). For listings with `capacity > 10` (e.g., large yoga classes), we still flag this as a future risk and route capacity logic through a single `reserveSpot(listingId, consumerId)` service function so a future migration to a `listing_slots` table is contained.

---

## 5. User flows

### Flow 1 — Consumer booking (revised for safety after reviews)

```
Step  Actor       Action
────  ─────       ───────────────────────────────────────────────────────────
 1    Consumer    Browses category → opens listing → taps Book
 2    Server      requireSession(); if absent, redirect to /signin?return_to=/book/[id]
 3    Server      reserveSpot(listingId, consumerId) → transaction:
                    SELECT listing FOR UPDATE; checks; INSERT bookings(status=pending);
                    COMMIT. Returns booking row. ~5–10ms.
 4    Server      stripe.checkout.sessions.create({
                    idempotencyKey: booking.id,
                    application_fee_amount: Math.floor((amountCents * 15) / 100),
                    transfer_data.destination: provider.stripe_account_id,
                    expires_at: now + 30min,           // Stripe's documented minimum
                    metadata.booking_id: booking.id
                  })

                  // pending_expires_at on our booking row is now 35 min (>30 to give
                  // the Stripe session time to expire naturally before our sweeper).
                  // If payment arrives for a booking we've cancelled, the §6.1
                  // "Webhook arrives after pending_expires_at" auto-refund path
                  // handles it.
 4a   Server      If Stripe call fails: updateBookingStatus(booking.id, 'pending', 'cancelled')
                  with cancellation_reason='system'; show retry UI.
 4b   Server      On success: UPDATE bookings SET stripe_checkout_session_id = ?
                                WHERE id = ? AND status = 'pending'
                  RETURNING id;
                  If row count = 0 (sweeper cancelled the booking during the Stripe call):
                    await stripe.checkout.sessions.expire(session.id);
                    render "This hold expired before payment could start — try again."
                  If row count = 1: redirect consumer to Stripe Checkout URL.
 5    Consumer    Pays on Stripe Checkout (hosted).
 6    Stripe      Two parallel things:
                    (a) redirects consumer to /bookings/[id]/success
                    (b) POSTs webhook checkout.session.completed
 7    /api/webhooks/stripe (Next.js route):
                  verify signature
                  → INSERT into payment_events (UNIQUE on event_id, ON CONFLICT DO NOTHING)
                  → await inngest.send({ name: 'stripe.event.received', data: { event_id }})
                  If Inngest send fails: return 500. Stripe will retry the webhook within
                  seconds; the UNIQUE constraint dedupes the row insert on retry.
                  On success: return 200.
                  THAT'S IT. No booking update here.
 8    Inngest     Handler for 'stripe.event.received':
                    look up booking by stripe_checkout_session_id
                    → updateBookingStatus(booking.id, 'pending', 'confirmed')
                      (sets confirmed_at = $now in the same UPDATE)
                    → emit Inngest event 'booking.confirmed'
                  Idempotent: if booking is already 'confirmed',
                  updateBookingStatus returns {alreadyApplied: true} — no-op.
                  Retries: Inngest retries with backoff on failure.
 9    Inngest     Handler for 'booking.confirmed':
                    send confirmation email via Resend
                    sleepUntil(booking.start_time - 1h) → send reminder email
10    Consumer    /bookings/[id]/success page reads booking row.
                  If status='pending', polls every 1s for up to 30s.
                  After 30s, switches to "Payment received — finalizing your
                  booking; we'll email you" with the booking ID.
                  The email is the authoritative receipt.
```

**Key correctness properties:**
- **Stripe Checkout Session creation is OUTSIDE the row lock.** The lock is held only during the DB transaction. SWE review caught this — earlier draft held the lock across the network call.
- **Webhook handler does only `INSERT payment_events` + fire Inngest event.** All state mutation happens in Inngest, which has retries and observability. Webhook handler is fast (sub-100ms) and Stripe-friendly. SWE-recommended pattern.
- **Idempotency end-to-end:** `idempotencyKey` on Stripe call, `UNIQUE stripe_event_id` on webhook insert, `WHERE status = $from` guard on Inngest update.
- **Stripe `checkout.session.expired` webhook** is handled by the same pipeline and releases inventory immediately rather than waiting for the 35-min Inngest sweep.

### Flow 2 — Provider onboarding

1. `/provider/signup` → email + business name + currency + timezone selection. **Commission disclosure shown prominently on this page** ("You keep 85% of every booking. We keep 15%. Frozen per booking at the time of sale."), and again on the listing-create price preview ("Original $120 · Discounted $80 · You receive $68 after our 15%").
2. Magic link login → INSERT users + providers rows; status=`pending_kyc`.
3. Server creates Stripe Connect Express account; generates onboarding link with `return_url` server-side keyed by provider_id (not in a session cookie — survives session expiry).
4. Provider completes Stripe-hosted KYC.
5. Provider returns to `/provider/onboarding/return` → "We'll email you when ready."
6. `account.updated` webhook → Inngest handler → flips `stripe_charges_enabled` / `stripe_payouts_enabled` → status `active` → email "You're live."
7. Provider can prepare draft listings during `pending_kyc` but cannot publish until status=`active`.
8. On first publish → `revalidateTag` + `revalidatePath` → listing appears in feeds immediately.
9. **Provider currency correction:** if the provider picked the wrong currency at signup AND no `pending`/`confirmed`/`completed`/`no_show` bookings exist yet, the provider can self-serve change it from settings. Active `pending` bookings block the self-serve change (they would otherwise settle in a stale currency at Stripe). After the first non-cancelled booking, currency lock requires an admin override (`/admin/providers/[id]` has a "change currency" action that warns "N historical bookings remain in OLD_CURRENCY; reports will mix currencies"; admin can confirm with a typed reason logged to an audit table).

### Flow 3 — Provider redemption (consumer arrives)

1. Consumer shows redemption code (e.g. `K7P9V8XR`) on their phone or in the email.
2. Provider opens `/provider/bookings/today` → list of today's confirmed bookings → taps Redeem (or types code).
3. Server Action:
   ```sql
   UPDATE bookings SET redeemed_at = $now, status = 'completed', updated_at = $now
   WHERE redemption_code = $1
     AND provider_id = $session_provider_id
     AND status = 'confirmed'
     AND redeemed_at IS NULL;
   -- $now passed from clock.now() per §3.1
   ```
   Affected row count is returned. Zero → user-facing "Code not valid for any active booking."
4. Both screens reflect "Redeemed."

### Flow 4 — Background (Inngest)

| Trigger | Job |
|---|---|
| Cron `* * * * *` (every minute) | Expire `pending` bookings past TTL → `cancelled` with reason `consumer_no_pay`; release inventory |
| Cron `* * * * *` | Expire active listings past `listing_expires_at` → status `expired`; revalidate caches |
| Cron `0 * * * *` (hourly) | Reconciliation: for each `pending` booking older than 1h, call `stripe.checkout.sessions.retrieve` and reconcile. Returns `pending` bookings to `confirmed` or `cancelled` based on Stripe's view |
| Cron `0 * * * *` (hourly) | **No-show → refund pipeline (single job, sequenced):** Job receives `$now` from `clock.now()`. (a) find `confirmed` bookings where `end_time + categories.no_show_grace_interval < $now` AND `redeemed_at IS NULL` → `updateBookingStatus('confirmed' → 'no_show')` → **immediately email the consumer**: "Your provider didn't redeem your booking. We're processing a full refund — it will show on your card within 5–10 business days." (b) issue Stripe refund as a separate Inngest step with its own retry policy → on `charge.refunded` webhook, `updateBookingStatus('no_show' → 'cancelled')` with reason `provider_no_show`. If the refund call fails after all retries, write a row to `payment_events` with `processing_error` set and alert admin; booking stays `no_show` until manual remediation. The two stages are in one job (not two daily crons) to keep ordering deterministic and the consumer email immediate. **`no_show_grace_interval`** is a per-category config (default 2h; restaurants 4h; fitness 1h) stored on `categories`. |
| Late-redemption recovery | If a provider scans a code for a booking that has flipped to `no_show`, the redemption Server Action checks for an in-flight refund; if none yet captured, it calls `stripe.refunds.cancel(refund.id)` then `updateBookingStatus('no_show' → 'completed')`. If the refund has already settled, redemption shows the provider: "This booking was refunded at HH:MM because it was past the grace window. Contact the consumer if they still want service." |
| Cron daily | Delete `payment_events` older than 90 days; copy event_id + type to archive table |
| Event `stripe.event.received` | Per-event-type handler updates state |
| Event `booking.confirmed` | Send confirmation email; schedule reminder for `start_time - 1h` via `step.sleepUntil` |

**The no-show → refund pipeline** is the only consumer-protection refund case we allow in v1. The hourly cadence (rather than daily) means the consumer hears within an hour of the no-show, not 24h+ later.

### Intentionally NOT in v1

- Reviews / ratings
- In-app chat between consumer and provider
- Push notifications
- Full-text search bar
- Saved listings / favorites
- Multiple time-slots per listing (covered by `capacity`)
- Multi-currency
- Native apps
- Provider self-serve price edits to listings with active bookings (provider must cancel and relist)

---

## 6. Error handling & edge cases

### 6.1 Payment & webhooks

**Stripe Checkout abandoned mid-payment.** Consumer closes the tab after booking is `pending` but before paying. Handled by `pending_expires_at = $now + 35min` + the minutely Inngest sweep + the Stripe `checkout.session.expired` webhook (whichever fires first releases inventory). Stripe Checkout Session itself has `expires_at = $now + 30min` (Stripe's documented minimum).

**Stripe Checkout Session creation fails.** Booking row already committed as `pending`. Server Action wraps `stripe.checkout.sessions.create` in try/catch; on failure, `updateBookingStatus(id, 'pending', 'cancelled')` with reason `system`, then UI shows a retry. The 35-min TTL is the backstop if the cleanup itself fails.

**Webhook delayed beyond 30s.** Success page polls 1s × 30s. After timeout, renders **"Payment received — finalizing your booking. You'll get an email."** Not an error. The booking confirmation email is the authoritative receipt.

**Webhook arrives twice or out of order.** `UNIQUE(stripe_event_id)` blocks duplicates at the table level. Inngest handler is defensive: if booking is already in the target state, no-op. `checkout.session.completed` is the source of truth for confirmation; later `payment_intent.succeeded` is logged but doesn't re-transition state.

**Payment succeeds but our processing fails.** Webhook handler: insert `payment_events`; try `inngest.send(...)`. If Inngest send fails AFTER the row is inserted, route a row to a `webhook_dead_letter` table (`stripe_event_id`, raw payload, first_failed_at, retries) AND return 200 to Stripe. A separate Inngest cron drains `webhook_dead_letter` by re-emitting the event. This prevents a transient Inngest outage from cascading into 3 days of Stripe retries hammering the same endpoint. If both Inngest send AND the DLQ insert fail (e.g., DB outage), return 500 — Stripe retries on its own schedule. Subsequent Inngest handler retries are governed by Inngest's backoff (default 4 attempts → ~1h). If everything still failing after Inngest retries, the hourly reconciliation job (§5.4) calls `stripe.checkout.sessions.retrieve` to reconcile `pending` bookings older than 1h. This is the one place we accept eventual consistency.

**In-app UX during the gap.** Any `pending` booking older than the 30s polling timeout renders in `/bookings` as "Processing — usually under an hour" with a manual "Refresh status" button that triggers an on-demand `stripe.checkout.sessions.retrieve` and updates state inline. **Rate limit:** server-side, max 1 refresh per booking per 10 seconds, max 6 per booking per hour. UI disables the button during the cooldown. Prevents a spam-click consumer from chewing through our Stripe API quota.

**Webhook arrives after `pending_expires_at` expired the booking.** Inngest handler sees booking is `cancelled` — refunds the charge automatically via `stripe.refunds.create`, logs to `payment_events`, emails consumer.

**Refund or chargeback initiated outside the app.** `charge.refunded` / `charge.dispute.created` webhooks flip booking to `cancelled`, log to events, email both parties. Redemption refuses non-`confirmed` bookings.

**Provider loses `charges_enabled`.** `account.updated` webhook flips `stripe_charges_enabled=false`. Inngest handler suspends all of that provider's `active` listings (`status='suspended'`), hides them from discovery, emails provider. In-flight `pending` bookings naturally fail at Stripe Checkout.

**Provider gets `account.deauthorized`.** Provider disconnected our Stripe app. Same handling as suspension; no future payouts possible. Inngest emails admin to follow up.

### 6.2 Concurrency

**Two consumers race for the last spot.** Both Server Actions enter the transaction; `SELECT FOR UPDATE` serializes. First INSERTs; second sees `count == capacity` and the action throws `SOLD_OUT`. The losing consumer sees "Just sold out — here are similar nearby" with server-rendered alternatives in the same category and city.

**Double-tap on Book.** Partial unique index rejects the second insert with Postgres error code `23505`. Server Action catches `23505`, looks up the existing pending booking, redirects to its existing Stripe Checkout Session URL. The user resumes their own checkout, never sees an error.

**Provider edits listing during in-flight pending booking.** Edits are split into two classes:
- **Non-material edits** (title, description, images, address text formatting): **allowed at any time.** These don't affect what a booked consumer is paying for.
- **Material edits** (`discounted_price_cents`, `original_price_cents`, `capacity`, `start_time`, `end_time`, `listing_expires_at`, `lat`/`lon` move, `category_id`): **blocked when active `pending` or `confirmed` bookings exist.** Provider sees an inline form-level message ("This listing has 3 active bookings. Cancel the listing to change price, time, or capacity."), not a generic toast. Server Action rejects with a typed error and the form re-renders with the message in place.
- If provider sets `status='cancelled'` on a listing with active bookings, we keep `pending` rows alive (Stripe could still confirm them); on confirmation, the no-show pipeline (§5.4) catches them within an hour.

**Redemption race vs. cancel.** Both go through the state-machine `updateBookingStatus(from='confirmed', to=...)`. First wins; second's UPDATE affects 0 rows; user sees a clear error.

**Provider double-tap redemption (or re-scan).** Second redemption attempt sees `redeemed_at IS NOT NULL`. Server Action returns `{ ok: false, code: 'ALREADY_REDEEMED', redeemed_at }`. UI shows: "Already redeemed at 7:42pm" (not a generic "code invalid" error). This avoids the failure mode where a consumer is standing at the counter, the provider says "your code doesn't work," and there's no path to verify it actually was redeemed.

### 6.3 Network & client

**Network fails on Book click.** Action never executed; nothing to clean up. Client shows retry; button is debounced and disabled-while-pending.

**Network fails between booking commit and Stripe call (Server Action retry).** `idempotencyKey: booking.id` on the Stripe call means a retried Server Action gets the same Checkout Session, not a duplicate.

**Network fails after Stripe Checkout returns.** On reopen, `/bookings` shows the pending booking; tapping resolves via the same success/polling page. Reconciliation job is the backstop.

**Listing expires while consumer is on the page.** Transaction re-validates `status='active' AND listing_expires_at > now()` and throws `LISTING_EXPIRED`. Client renders "Just expired" with alternatives. Belt-and-braces: consumer feed queries filter `listing_expires_at > now()` directly, not trusting the sweeper.

**Browser Back from Stripe Checkout.** Consumer lands back on listing page; partial unique index ensures their pending booking is reused on next tap (same handling as double-tap).

### 6.4 Auth & generic

**Auth.js session expires mid-checkout.** Stripe Checkout is hosted; doesn't need our session. On return, success page routes through magic-link sign-in with `return_to` set to the success URL (allowlist-validated). Booking lookup by `stripe_checkout_session_id` works regardless.

**Auth.js session expires mid-onboarding (provider).** Onboarding state is keyed by `provider_id` server-side. Return URL is resolvable from `state` query param Stripe round-trips for us, not from session cookies.

**Server Action throws unexpected error.** Wrapped in `withErrorBoundary` — catches, logs to Sentry with `userId` and action name, returns `{ ok: false, code: 'INTERNAL' }`. Client shows generic toast. Never leak stack traces.

**Magic link `return_to` allowlist.** Single source of truth: the regex defined in §3.3, owned by `lib/auth/return-to.ts`. Do not duplicate the pattern anywhere else in the codebase or docs.

**Invalid input from provider.** Zod schema rejects: `capacity < 1`, `discounted_price_cents < 50` (Stripe's per-transaction minimum), `discounted_price_cents >= original_price_cents`, `start_time <= now()`, `listing_expires_at > end_time`, `images.length < 1 || images.length > 10`, individual image URL must be HTTPS and match the host allowlist (`<our-bucket>.s3.amazonaws.com`, our CDN domain, `images.unsplash.com` for stock placeholders — list lives in `lib/config/image-hosts.ts`), `title.length > 120`, `description.length > 5000`, RTL override/control characters stripped (`‮`, `‭`, etc), emoji explicitly **allowed** in title/description and tested in email rendering (emoji shouldn't break email subject lines — verified in §7.6 smoke). Server-side enforcement; never trust client validation. **Image swap UX:** the listing-edit form stages changes client-side and submits atomically, so deleting all images and adding new ones in one edit session is valid as long as the final array length is 1-10.

### 6.5 Background jobs

**Inngest job fails.** Retries with backoff (4 attempts). Persistent failures alert via email + Sentry. Jobs are idempotent: pending-expiry re-checks `pending_expires_at < now()` inside its own transaction; reconciliation rechecks status before acting.

**Email send fails (Resend down).** Resend client throws; Inngest step retries up to 5 times over 1h. Confirmation emails are nice-to-have; in-app `/bookings` is the authoritative source. Reminder failures past retries are dropped + logged.

**Inngest sweep drift.** Listings could appear up to 60s past expiry. Mitigation: every consumer-facing query also filters `listing_expires_at > now()` directly.

### 6.6 Authorization-specific edge cases

**Consumer A tries to read consumer B's booking.** `requireOwnership` throws; route renders 404 (not 403, to avoid existence leak).
**Provider A tries to redeem provider B's booking.** Redemption SQL includes `AND provider_id = $session_provider`. Affects zero rows → user-facing error.
**Logged-out POST to booking Server Action.** `requireSession()` rejects.
**Open-redirect via `return_to`.** Allowlist regex catches; falls back to `/`.
**CSRF via cross-site Server Action invocation.** Auth.js CSRF token enforced; integration test asserts a forged request returns 403.

---

## 7. Testing approach

We optimize for two things: **confidence in the booking transaction** and **confidence in Stripe webhook handling.** Everything else gets the lightest test that proves it works.

### 7.1 Unit tests — Vitest

Pure functions only; no DB, no network. Colocated as `*.test.ts`.

- `lib/pricing.ts` — fee split math, currency rounding (floor-to-platform residual).
- `lib/redemption-code.ts` — generation alphabet (excluding ambiguous chars), length, collision odds.
- `lib/validation/*.ts` — Zod schemas for listing/booking input.
- `lib/format.ts` — money/date formatters; tz-aware date rendering.
- `lib/state-machine/booking.ts` — `updateBookingStatus` valid/invalid transitions.

Tests that need to mock Prisma, Stripe, or Resend belong in integration.

### 7.2 Integration tests — Vitest + Testcontainers

**One Postgres container per Vitest worker** (not per test) — pulled once, migrated once at worker startup. Each test wraps its work in a transaction or `TRUNCATE`s tables in a `beforeEach`. Stripe + Resend stubbed at the SDK boundary with `vi.mock`. Lives in `tests/integration/*.test.ts`. Uses an injectable clock. CI budget assumes ~5s container cold-start × N workers (where N = CPU count, typically 4 on GH Actions) — amortized across the whole suite, well within the 8-minute target.

- **Booking transaction:** capacity, status, expiry, partial-unique violation on double-book, `SOLD_OUT` on full capacity, `LISTING_EXPIRED` on expired listing.
- **Webhook handlers:** for each event type, idempotency (replaying same `stripe_event_id` no-ops), state transitions, out-of-order events don't corrupt state.
- **Inngest job handlers as plain functions:** pending-expiry releases capacity; listing-expiry flips status; reconciliation correctly flips `pending` whose Stripe session is `complete`; auto-refund job issues refund + updates state.
- **State machine:** every illegal `updateBookingStatus` transition throws; legal transitions succeed.
- **Authorization helpers:** `requireOwnership` for booking and listing; `return_to` allowlist regex.

### 7.3 Concurrency tests

`tests/integration/concurrency.test.ts`. Spin up N=10 parallel `Promise.all` invocations of the booking Server Action against a `capacity=1` listing → assert exactly 1 succeeds, 9 fail with `SOLD_OUT`. Repeat with `capacity=3`, assert exactly 3 succeed. Uses real Postgres (mocking defeats the point). Run with `--repeat 20` nightly.

### 7.4 Stripe webhook simulation

- **Fixtures in CI:** captured raw payloads in `tests/fixtures/stripe/*.json`, signed with a test secret using Stripe's algorithm via `stripe.webhooks.generateTestHeaderString`. Fast, deterministic, every PR.
- **Stripe CLI in local dev:** `stripe listen --forward-to localhost:3000/api/webhooks/stripe` in `CONTRIBUTING.md`. Not in CI.

### 7.5 E2E tests — Playwright

Two specs only; Stripe Test mode against a dedicated account.

- `tests/e2e/consumer-booking.spec.ts`: sign up → browse → open listing → book → pay (`4242 4242 4242 4242`) → success → booking in `/bookings`.
- `tests/e2e/provider-redemption.spec.ts`: provider sign-in → today's bookings → enter code → marked redeemed.

Auth via Playwright `storageState` seeded by setup script — no magic-link dance.

### 7.6 Manual smoke — pre-release checklist

`docs/release-checklist.md`:
- Real Stripe Test end-to-end on staging (book → refund via dashboard → assert cancelled).
- All transactional emails opened in Gmail, Apple Mail, Outlook web — visual check.
- Provider Connect Express onboarding on fresh email.
- Mobile Safari smoke on booking flow.

### 7.7 Explicitly skipped in v1

Visual regression (Percy/Chromatic), load/perf (k6), full WCAG audit. We do run `eslint-plugin-jsx-a11y` to catch obvious violations.

### 7.8 CI structure

- **Every PR:** typecheck, lint, unit, integration, E2E, concurrency suite at `--repeat 3` (catches the worst flake before merge). Target ≤ 8 minutes via parallel shards.
- **Nightly on main:** concurrency suite at `--repeat 20`; Playwright across Chromium + WebKit; dependency audit.
- **On deploy to prod:** Playwright smoke against production with a dedicated test account, blocking on failure.

### 7.9 Coverage target

**70% line coverage, enforced on `lib/` and `app/api/` only.** UI components excluded — chasing JSX coverage produces brittle tests. 70% is the level where the booking transaction, webhook handlers, pricing, and validation are fully covered (~80% of `lib/` LOC) without forcing tests on trivial getters. Concurrency and webhook idempotency tests matter more than the percentage.

---

## 8. Open risks & deferred work

| Item | Risk | Defer to |
|---|---|---|
| Capacity-on-listings breaks for capacity > ~10 | Throughput bottleneck on hot listings | When inventory data shows it |
| Cold-start with category browse on day one | Empty feeds hurt early retention | Mitigate via mixed "starting soon" landing surface; consider per-category soft-launches |
| Strict non-refundable across all verticals | Trust risk in restaurants/services | Watch consumer complaints; revisit refund window per category |
| Multi-currency | Limits geographic expansion | When we add a second market |
| Reviews / ratings | Trust signal gap | v1.1 |
| Search bar | Discovery gap | When inventory exceeds category-browse scrollable size |
| QR redemption | Friction in busy venues | v1.1 |
| Native apps | Push notifications, app store presence | After we validate demand |
| Per-category `metadata` schema validation | Garbage data risk | Add `pg_jsonschema` once >3 categories live |
| Image management (deletes/CDN purge) | Storage leak | When provider edit/delete volume warrants |

---

## 9. Provenance

This spec was developed in collaboration with the user via the `superpowers:brainstorming` skill. Sections 1–3 were drafted live; Sections 4 and 5 were drafted by a worker subagent. The spec was independently reviewed by three subagents (PM, Senior Engineer, QA) before finalization, and their critical findings are incorporated throughout — particularly the webhook architecture in Flow 1, the cross-cutting concerns in §3, the state-machine enforcement, and the authorization model.
