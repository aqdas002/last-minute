# M2 — Provider Onboarding + Stripe Connect Express Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Plan philosophy:** This plan trusts the executor knows Spring Boot 3 + JPA + Maven (M1 established the patterns). It defines what changes, the test matrix per task, and the new files — not every line of Spring boilerplate. Refer to M1 plan (`2026-05-27-m1-spring-boot-foundation.md`) for the established conventions.

**Goal:** A real local merchant can self-serve sign up via magic link, complete Stripe Connect Express KYC, see a clear 15% commission disclosure, and publish a listing that appears in the consumer feed. Admin can correct a provider's currency if they pick wrong.

**Architecture changes vs. M1:** Adds `/api/providers/**` for provider self-onboarding, `/api/webhooks/stripe` for Stripe events, `payment_events` + `admin_actions` + `webhook_dead_letter` tables, and Spring `@TransactionalEventListener` for the in-process webhook → state pipeline (the Inngest equivalent). Frontend gets `/provider/signup`, `/provider/dashboard`, `/provider/listings`, and `/admin/providers/[id]` pages.

**Tech additions:** Stripe Java SDK (`com.stripe:stripe-java`), nothing else.

**Canonical references:**
- Spec: `docs/superpowers/specs/2026-05-26-last-minute-booking-design.md`
- M1 plan: `docs/superpowers/plans/2026-05-27-m1-spring-boot-foundation.md`
- M1 retro: `docs/superpowers/retros/2026-05-28-m1-retro.md`
- Milestone draft (scope reference, stale for stack): `.brainstorm-draft/implementation-plan-draft.md`

---

## Prerequisites

- M1 complete + green on `main`.
- Stripe test-mode account with Connect enabled. Set:
  - `STRIPE_SECRET_KEY=sk_test_...`
  - `STRIPE_WEBHOOK_SECRET=whsec_...` (from `stripe listen` or dashboard)
  - `STRIPE_PUBLISHABLE_KEY=pk_test_...` (frontend only)
- Stripe CLI installed for local webhook forwarding: `brew install stripe/stripe-cli/stripe`.

---

## Task 1 — Fix M1 retro bugs (block 1: validation error response, frontend title)

**Files:**
- Create: `backend/src/main/java/com/lastminute/common/ApiExceptionHandler.java`
- Modify: `frontend/index.html`
- Test: `backend/src/test/java/com/lastminute/auth/MagicLinkValidation_HttpIT.java` (real HTTP via `WebApplicationContext` to confirm 400, not just MockMvc)

- [ ] **Step 1: `ApiExceptionHandler.java`**

```java
package com.lastminute.common;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
    Map<String, String> fieldErrors = new HashMap<>();
    ex.getBindingResult().getFieldErrors()
        .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("error", "validation_failed", "fields", fieldErrors));
  }
}
```

- [ ] **Step 2: HTTP-layer test (uses `TestRestTemplate`, not MockMvc, so the full filter chain runs as in prod)**

```java
package com.lastminute.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.lastminute.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MagicLinkValidationHttpIT extends IntegrationTestBase {

  @Autowired private TestRestTemplate http;

  @Test
  void invalid_email_returns_400() {
    ResponseEntity<String> res =
        http.postForEntity("/api/auth/magic/request", Map.of("email", "not-an-email"), String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(res.getBody()).contains("validation_failed");
  }

  @Test
  void empty_body_returns_400() {
    ResponseEntity<String> res =
        http.postForEntity("/api/auth/magic/request", Map.of(), String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
```

- [ ] **Step 3: frontend title**

Edit `frontend/index.html`:
```html
<title>Last Minute</title>
<meta name="description" content="Last-minute booking marketplace — discounted deals before they expire." />
```

- [ ] **Step 4: commit**

```bash
git add -A
git commit -m "fix(api,ui): validation errors return 400 with structured body; frontend title"
```

---

## Task 2 — Flyway V3: `admin_actions`, `payment_events`, `webhook_dead_letter` tables

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__webhooks_and_audit.sql`

- [ ] **Step 1: write the migration**

```sql
-- Webhook event log (idempotency + audit). Per spec §4.
CREATE TABLE payment_events (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id       UUID,                                            -- nullable: some events arrive without booking context
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
  action           TEXT NOT NULL,            -- e.g. "provider.change_currency"
  target_id        UUID,
  reason           TEXT NOT NULL,
  payload          JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX admin_actions_actor_idx ON admin_actions (actor_user_id, created_at DESC);
```

- [ ] **Step 2: extend `DbTruncate` to wipe new tables (FK-safe order)**

In `backend/src/test/java/com/lastminute/support/DbTruncate.java`, expand the TRUNCATE list:
```sql
TRUNCATE TABLE
  webhook_dead_letter,
  payment_events,
  admin_actions,
  verification_tokens,
  listings,
  providers,
  categories,
  users
RESTART IDENTITY CASCADE
```

- [ ] **Step 3: apply + verify**

```bash
cd backend && JAVA_HOME=/home/linuxbrew/.linuxbrew/opt/openjdk@21 sg docker -c './mvnw -B test'
```

Expected: 78 tests pass (76 from M1 + 2 from Task 1's HTTP-layer tests).

- [ ] **Step 4: commit**

```bash
git add -A && git commit -m "feat(db): Flyway V3 — payment_events, webhook_dead_letter, admin_actions tables"
```

---

## Task 3 — Add `stripe-java` + `StripeConfig` + `StripeService`

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/lastminute/stripe/StripeConfig.java`
- Create: `backend/src/main/java/com/lastminute/stripe/StripeService.java`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: dependency**

```xml
<dependency>
  <groupId>com.stripe</groupId>
  <artifactId>stripe-java</artifactId>
  <version>28.5.0</version>
</dependency>
```

- [ ] **Step 2: `application.yml` add**

```yaml
app:
  stripe:
    secret-key: ${STRIPE_SECRET_KEY:}
    webhook-secret: ${STRIPE_WEBHOOK_SECRET:}
```

- [ ] **Step 3: `StripeConfig.java`**

```java
package com.lastminute.stripe;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {
  private static final Logger LOG = LoggerFactory.getLogger(StripeConfig.class);
  @Value("${app.stripe.secret-key:}") private String secretKey;

  @PostConstruct void init() {
    if (secretKey == null || secretKey.isBlank()) {
      LOG.warn("STRIPE_SECRET_KEY is empty — Stripe calls will throw. OK in test/dev without Stripe.");
      return;
    }
    Stripe.apiKey = secretKey;
  }
}
```

- [ ] **Step 4: `StripeService.java`** — wraps Connect Express onboarding (creates account, generates onboarding link). Public methods: `createConnectedAccount(email, country) → accountId`, `createAccountOnboardingLink(accountId, returnUrl, refreshUrl) → url`. Trust the Stripe SDK; no test coverage at the service layer (covered via WireMock in Task 7).

- [ ] **Step 5: compile + commit**

```bash
git add -A && git commit -m "feat(stripe): Stripe SDK + StripeService wrapper for Connect Express"
```

---

## Task 4 — Provider self-signup endpoint + flow

**Files:**
- Create: `backend/src/main/java/com/lastminute/providers/ProviderController.java`
- Create: `backend/src/main/java/com/lastminute/providers/ProviderService.java`
- Test: `backend/src/test/java/com/lastminute/providers/ProviderControllerIT.java`

- [ ] **Step 1: `ProviderService.requestSignup(email, businessName, currency, timezone)`** — creates `users(role=provider)` + `providers(status=pending_kyc)`, then calls `MagicLinkService.request(email, "/provider/onboarding")` so the next click lands them on Stripe onboarding (Task 5).

- [ ] **Step 2: `ProviderController` exposes `POST /api/providers/signup` with Bean Validation** (`@Email`, `@NotBlank`, `@Size(min=3,max=3)` for currency). CSRF-ignored via the auth allowlist.

- [ ] **Step 3: Tests (5)** —
  - successful signup creates both rows with status `pending_kyc`
  - duplicate email returns 409
  - invalid email returns 400 (validates Task 1's exception handler)
  - currency not 3 chars returns 400
  - missing timezone returns 400

- [ ] **Step 4: commit**

```bash
git add -A && git commit -m "feat(providers): /api/providers/signup creates user+provider+sends magic link"
```

---

## Task 5 — Stripe Connect Express onboarding + return URL

**Files:**
- Modify: `backend/src/main/java/com/lastminute/providers/ProviderController.java`
- Create: `backend/src/main/java/com/lastminute/providers/OnboardingController.java`
- Test: `backend/src/test/java/com/lastminute/providers/OnboardingControllerIT.java` (WireMock the Stripe SDK calls)

Endpoints:
- `POST /api/providers/onboarding/link` — authenticated provider only; calls `StripeService.createConnectedAccount` if `stripe_account_id` is null, persists the id, then generates an onboarding link with `return_url=<frontend>/provider/onboarding/return` and `refresh_url=<frontend>/provider/onboarding`. Returns `{"url": "..."}`.
- `GET /api/providers/onboarding/state` — returns `{stripe_account_id, charges_enabled, payouts_enabled, status}` so the frontend return page can poll.

Tests use WireMock to stub the Stripe REST surface:
- Onboarding link returns 200 + URL; provider row updated with account_id
- Calling twice reuses the same account_id (idempotent)
- State endpoint returns the persisted flags

```bash
git add -A && git commit -m "feat(stripe): provider onboarding link + state endpoints"
```

---

## Task 6 — `/api/webhooks/stripe` + `WebhookController` + signature verification

**Files:**
- Create: `backend/src/main/java/com/lastminute/webhooks/WebhookController.java`
- Create: `backend/src/main/java/com/lastminute/webhooks/PaymentEvent.java` (JPA entity)
- Create: `backend/src/main/java/com/lastminute/webhooks/PaymentEventRepository.java`
- Create: `backend/src/main/java/com/lastminute/webhooks/StripeEventReceived.java` (Spring application event)
- Create: `backend/src/main/java/com/lastminute/webhooks/WebhookDeadLetterRepository.java`
- Test: `backend/src/test/java/com/lastminute/webhooks/WebhookControllerIT.java`

Controller behaviour (matches spec §5 Flow 1 step 7 + §6.1):

```java
@PostMapping("/api/webhooks/stripe")
public ResponseEntity<Void> stripe(
    @RequestHeader("Stripe-Signature") String sig,
    @RequestBody String rawBody) {
  Event event;
  try {
    event = Webhook.constructEvent(rawBody, sig, webhookSecret);
  } catch (SignatureVerificationException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
  }

  try {
    PaymentEvent saved = events.save(PaymentEvent.from(event));
    publisher.publishEvent(new StripeEventReceived(saved.getId(), event.getType()));
    return ResponseEntity.ok().build();
  } catch (DataIntegrityViolationException dup) {
    // UNIQUE(stripe_event_id) — already processed, return 200 so Stripe stops retrying.
    return ResponseEntity.ok().build();
  } catch (Exception publishFail) {
    // DLQ: insert and 200 so Stripe doesn't hammer us during transient outages.
    deadLetter.save(new WebhookDeadLetter(event, publishFail.getMessage()));
    return ResponseEntity.ok().build();
  }
}
```

Tests use Stripe's `Webhook.signPayload(...)` helper to build valid signatures; cover:
- valid signature, valid event → 200 + `payment_events` row + Spring event published
- invalid signature → 400, no row
- duplicate `stripe_event_id` → 200, no second row (idempotent)
- publish failure → 200 + row in `webhook_dead_letter`

```bash
git add -A && git commit -m "feat(webhooks): /api/webhooks/stripe with sig verification + DLQ"
```

---

## Task 7 — `account.updated` handler flips provider status

**Files:**
- Create: `backend/src/main/java/com/lastminute/providers/AccountUpdatedHandler.java`
- Test: `backend/src/test/java/com/lastminute/providers/AccountUpdatedHandlerIT.java`

`@TransactionalEventListener` on `StripeEventReceived`. If `event.type == "account.updated"`:
- Look up provider by `stripe_account_id` from `event.data.object.id`
- Update `stripe_charges_enabled`, `stripe_payouts_enabled` from the event
- If both are true AND status is `pending_kyc` → flip to `active`; mark `processed_at` on the event row
- Send "You're live" email via `ResendClient` (extend the client with `sendProviderLive(to)`)

Tests:
- account.updated with both enabled → status flips to active + email sent
- account.updated with charges_enabled=false → status stays pending_kyc
- account_id not found → log + mark processed (don't fail)
- Re-running the handler on the same event (idempotent because `payment_events.processed_at IS NULL` check) → second call is a no-op

```bash
git add -A && git commit -m "feat(providers): account.updated handler flips KYC status + emails"
```

---

## Task 8 — Provider listing CRUD + commission disclosure preview endpoint

**Files:**
- Create: `backend/src/main/java/com/lastminute/providers/ProviderListingController.java`
- Create: `backend/src/main/java/com/lastminute/providers/ProviderListingService.java`
- Test: `backend/src/test/java/com/lastminute/providers/ProviderListingControllerIT.java`

Endpoints (all `ROLE_PROVIDER`-gated; provider can only act on their own listings):
- `GET /api/providers/me/listings` — list mine
- `POST /api/providers/me/listings` — create (status=draft if KYC pending; status=active if KYC complete)
- `PATCH /api/providers/me/listings/{id}` — material vs non-material split:
  - Non-material (title/description/images): always allowed
  - Material (price/time/capacity/location/category): allowed only if no `pending`/`confirmed` bookings exist. M2 has no bookings yet, so the predicate is `bookings.count == 0` for this listing. M3 will tighten the predicate; the rejection path + error code (`HAS_ACTIVE_BOOKINGS`) is wired here.
- `POST /api/providers/me/listings/{id}/publish` — flip draft → active; rejects if `stripe_charges_enabled=false`.
- `GET /api/providers/me/listings/preview-fee?priceCents=N` — returns `{platformFeeCents, providerPayoutCents}` using `PricingService` for the disclosure UI.

Tests (~8): happy path, material edit allowed, material edit blocked when bookings exist (with a manual booking row insert), publish gated on charges_enabled, preview-fee matches §3.2 boundary values.

```bash
git add -A && git commit -m "feat(providers): self-serve listing CRUD + material-edit split + fee preview"
```

---

## Task 9 — Provider currency self-correct + admin currency-correction tool

**Files:**
- Create: `backend/src/main/java/com/lastminute/admin/AdminProviderController.java` (extend existing AdminController package)
- Create: `backend/src/main/java/com/lastminute/admin/AdminAction.java` (JPA entity for `admin_actions`)
- Create: `backend/src/main/java/com/lastminute/admin/AdminActionRepository.java`
- Tests: extend `AdminControllerIT` with currency override cases

Self-correct:
- `PATCH /api/providers/me/settings/currency` — provider-only; allowed IFF no `pending|confirmed|completed|no_show` bookings exist for any of their listings (M2: bookings table is empty so trivially allowed; integration test confirms M3 readiness by inserting a booking row directly).

Admin override:
- `POST /api/admin/providers/{providerId}/currency` — admin-only; body `{currency, reason}`; rejects if `reason.length < 10`; writes an `admin_actions` row with `action="provider.change_currency"` and a payload including old + new currency. Returns the audit row id.

Tests: self-correct happy path, self-correct rejected after a booking exists, admin happy path writes audit row, admin missing reason → 400.

```bash
git add -A && git commit -m "feat(admin,providers): currency self-correct + admin override w/ audit"
```

---

## Task 10 — Frontend: provider signup + onboarding return + dashboard + listing CRUD

**Files (frontend):**
- Create: `src/api/providers.ts`
- Create: `src/pages/provider/signup.tsx`
- Create: `src/pages/provider/onboarding.tsx` (initiate)
- Create: `src/pages/provider/onboarding-return.tsx` (post-Stripe)
- Create: `src/pages/provider/dashboard.tsx`
- Create: `src/pages/provider/listings.tsx` (list + create + edit)
- Modify: `src/App.tsx` (add routes)
- Modify: `src/components/header.tsx` (add "For providers" link)

`/provider/signup` form mirrors the backend request: email + businessName + currency dropdown (USD/EUR/GBP for M2) + timezone dropdown (TZ DB strings). **Commission disclosure block above the submit button: "You keep 85% of every booking. We keep 15%. Frozen per booking at the time of sale."**

`/provider/onboarding` calls `POST /api/providers/onboarding/link` and `window.location.assign(res.url)`.

`/provider/onboarding/return` polls `GET /api/providers/onboarding/state` every 2 seconds (max 30s) until `charges_enabled=true`, then redirects to `/provider/dashboard`. Shows a "Stripe verification can take a few minutes" message in the meantime.

`/provider/dashboard` shows status (pending KYC vs live) + a "Create listing" CTA.

`/provider/listings` is a single page with a form to create + a table to list/edit. The price preview component calls `GET /api/providers/me/listings/preview-fee?priceCents=...` and shows "You receive $X.XX after our 15%" live as the user types.

No frontend unit tests in M2 — vite build + tsc is the floor; Playwright E2E lands in M6.

```bash
git add -A && git commit -m "feat(frontend): provider signup, onboarding, dashboard, listing CRUD + fee preview"
```

---

## Task 11 — `webhook_dead_letter` drain job

**Files:**
- Create: `backend/src/main/java/com/lastminute/webhooks/DeadLetterDrainJob.java`
- Test: `backend/src/test/java/com/lastminute/webhooks/DeadLetterDrainJobIT.java`

`@Scheduled(fixedRate = 30_000)` reads up to 100 DLQ rows, re-publishes the `StripeEventReceived` event for each, deletes on success, increments retries on failure. After 20 retries, log + alert (Sentry message); don't infinite-loop.

Idempotent because the handler chain (Task 7) checks `payment_events.processed_at` before acting.

Test: insert DLQ row → run job manually (extract a `drainNow()` method for tests) → assert row deleted + handler ran.

```bash
git add -A && git commit -m "feat(webhooks): DLQ drain @Scheduled job (30s rate) with 20-retry cap"
```

---

## Task 12 — End-to-end smoke + CI verification

- [ ] **Step 1:** Start backend with `dev-seed` profile + the test Stripe keys.
- [ ] **Step 2:** Run `stripe listen --forward-to localhost:8080/api/webhooks/stripe` in another terminal; copy the printed webhook secret into `STRIPE_WEBHOOK_SECRET` env and restart backend.
- [ ] **Step 3:** Manually walk:
  1. Frontend `/provider/signup` — submit your email. Check console for magic link, click it.
  2. Land on `/provider/onboarding`. Click "Continue". Complete Stripe-hosted Express onboarding with test data (Stripe doc: fake SSN 000-00-0000, etc.).
  3. Return to `/provider/onboarding/return`; verify it polls and lands on `/provider/dashboard` as "Live".
  4. Create a listing. Verify the fee preview shows "$X.XX after 15%".
  5. Refresh `/` — see the listing in the consumer feed.
- [ ] **Step 4:** Force `stripe trigger account.updated` and verify provider row's `stripe_charges_enabled` flips.
- [ ] **Step 5:** Push a branch + open a PR. Verify GH Actions CI runs both jobs green (first real CI exercise).
- [ ] **Step 6:** Tag `v0.2.0-m2` after PR merges.

---

## Acceptance criteria

| # | Criterion | Tasks |
|---|---|---|
| 1 | Provider self-signup creates user+provider, sends magic link | 4 |
| 2 | Stripe Connect Express onboarding works end-to-end on test mode | 3, 5, 6, 7 |
| 3 | `account.updated` webhook flips provider status to `active` + sends "you're live" email | 6, 7 |
| 4 | Commission disclosure visible on signup + listing-create preview | 10 |
| 5 | Provider can self-CRUD listings; material edit blocked when bookings exist (predicate ready for M3) | 8 |
| 6 | Currency self-correct + admin override with audit log in `admin_actions` | 9 |
| 7 | Webhook DLQ + drain @Scheduled job survives transient outages | 6, 11 |
| 8 | Validation errors return 400 with structured body (M1 retro fix) | 1 |
| 9 | CI green on first real PR | 12 |

## Self-review

- File paths use `backend/src/main/java/com/lastminute/...` and `frontend/src/...` consistently.
- New JPA entities follow the M1 enum + audit pattern (`@JdbcType(PostgreSQLEnumJdbcType.class)`, `@CreationTimestamp`, JOIN FETCH where DTOs serialize).
- New endpoints follow the M1 security model: `/api/auth/**` + `/api/webhooks/**` + `/api/admin/**` + the new `/api/providers/**` go into the CSRF-ignored set; `/api/providers/me/**` and `/api/admin/**` are role-gated.
- Bean Validation errors flow through the new `ApiExceptionHandler` (Task 1).
- Spring application events replace Inngest: webhook controller writes-and-publishes; handlers `@TransactionalEventListener` (so they only run on commit) and check `processed_at IS NULL` for idempotency.

## Execution handoff

Plan saved to `docs/superpowers/plans/2026-05-28-m2-provider-onboarding.md`.

Recommended execution batching:
- **Batch 1:** Tasks 1, 2 (retro fixes + new tables)
- **Batch 2:** Tasks 3, 4, 5 (Stripe + provider signup + onboarding endpoints)
- **Batch 3:** Tasks 6, 7, 11 (webhook + handler + DLQ drain)
- **Batch 4:** Tasks 8, 9 (provider listing CRUD + currency tools)
- **Batch 5:** Tasks 10, 12 (frontend + manual smoke + first real CI run)

Estimated solo wall time: **~3 days** for M2 (backend ~2, frontend ~0.5, smoke + CI ~0.5).
