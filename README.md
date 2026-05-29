# Last Minute

> A marketplace for last-minute bookings. Providers list perishable inventory at a discount; consumers grab tonight's deals before they expire.

## The problem

Restaurants close with empty tables. Fitness studios run classes with empty mats. Hotels finish the day with unsold rooms. Salons and tutors block off a slot that no one takes. Across every category of perishable service, inventory that isn't sold by some deadline becomes worth zero.

The traditional fix — discount aggressively, hope the right consumer finds you in time — is fragmented across a dozen vertical-specific apps (HotelTonight for rooms, OpenTable for restaurants, Mindbody for fitness, etc.). Each takes a cut, none of them talk to each other, and providers spend more time managing channels than serving customers.

## What this is

A single two-sided web marketplace that works for **any** kind of last-minute perishable inventory:

- **Providers** post their unsold slots with a discounted price and a cutoff time. One dashboard, one onboarding, one commission rate, regardless of vertical.
- **Consumers** browse a "starting soon near you" feed (and category pages for when they know what they want), book through the platform with their card, and show a redemption code when they arrive.

We collect payment through Stripe Connect, take a flat **15% commission**, and pay out the rest to the provider. Bookings are **non-refundable from the consumer side** (matches the HotelTonight model) — with one safety net: if the provider doesn't redeem the booking within a per-category grace window, we auto-refund the consumer in full and email them so they're not left guessing.

## Who it's for

| Role | Why they show up |
|---|---|
| **Consumers** | Spontaneous, price-sensitive, mobile-first. Looking for tonight's deal, not next month's reservation. |
| **Providers** | Small operators (restaurants, salons, classes, tour guides, hotels) who have last-minute capacity they can't otherwise move. Want a self-serve listing flow that takes minutes, not a sales call. |
| **Platform** | Earns commission on every transaction; no listing fees or subscriptions. |

## Status

This is a working MVP being built in vertical milestones. **Single-city pilot first, generic-across-categories from day one.**

| Milestone | Scope | State |
|---|---|---|
| M1 | Auth (magic-link), category browse, listing detail, admin-seeded inventory | ✅ shipped |
| M2 | Provider self-onboarding via Stripe Connect Express + listing CRUD + commission disclosure UI | ✅ shipped |
| M3 | Consumer booking + payment via Stripe Checkout (the riskiest slice — concurrency, webhooks, idempotency) | ✅ shipped |
| M4 | Provider redemption (8-char code) + refund/chargeback webhook handlers | ✅ shipped |
| M5 | Keyword search across listings + T-1h booking reminder email job | ✅ shipped |
| M6 | Provider 30-day revenue summary on dashboard | ✅ shipped |
| M7 | Provider all-bookings history page with status filters | ✅ shipped |
| M8 | Booking confirmation email on first webhook (replay-safe) | ✅ shipped |
| M9 | Consumer self-service refund request ("provider didn't honor my booking") + auto-close on charge.refunded webhook | ✅ shipped |
| M10 | Admin refund-request queue + deny/notes endpoints | ✅ shipped |
| M11 | Provider listing edit UI (with active-bookings safeguard) + admin refund queue UI | ✅ shipped |
| M12 | Pre-launch hardening: real Stripe smoke test, Sentry wiring, release checklist | upcoming |

## Architecture

**Monolithic** by design, not microservices. One Spring Boot service + one React SPA + one Postgres database. Designed so a single full-stack engineer can ship and operate it without a platform team.

```
┌────────────┐  HTTPS / JSON  ┌──────────────────────┐   JDBC   ┌──────────┐
│ React SPA  │ ◄────────────► │  Spring Boot 3       │ ◄──────► │ Postgres │
│ (Vite)     │  (session      │  Java 21, JPA, Flyway│          │ (Neon)   │
│  Vercel    │   cookies)     │  Fly.io              │          └──────────┘
└────────────┘                └──────────┬───────────┘
                                         │
                                         ▼
                         ┌───────────────────────────┐
                         │ Stripe Connect · Resend · │
                         │ Sentry · Spring @Scheduled│
                         └───────────────────────────┘
```

### Tech stack

**Backend** (`backend/`): Java 21 LTS · Spring Boot 3 · Spring Data JPA · Flyway · Spring Security 6 (magic-link + Google OAuth + Spring Session JDBC) · Stripe Java SDK · Resend · Caffeine cache · Sentry · JUnit 5 + AssertJ + Testcontainers + MockMvc + WireMock + ArchUnit.

**Frontend** (`frontend/`): React 19 · Vite · TypeScript (strict) · React Router 7 · TanStack Query · Tailwind v4.

**Why monolithic:** see the design spec §2. A single deploy, a single language stack per side, and Postgres as the source of truth for everything — money, state, sessions — keeps the operational surface small enough for solo development. There's no decision in here that paints us into a corner if traffic ever forces a hot path into its own service later.

## Quick start

You'll need Java 21, Maven, pnpm, Docker (for Testcontainers), and a local or hosted Postgres.

```bash
# 1. Local Postgres (any flavour — examples below)
docker run --name lastminute-pg -e POSTGRES_USER=lastminute -e POSTGRES_PASSWORD=lastminute -e POSTGRES_DB=lastminute -p 5432:5432 -d postgres:16-alpine
# (or use Homebrew's postgresql@16 — see docs/superpowers/specs/ for env config)

# 2. Backend — boots, runs Flyway migrations, seeds an admin user + one demo listing
cd backend
SPRING_PROFILES_ACTIVE=dev-seed ./mvnw spring-boot:run

# 3. Frontend (new terminal)
cd frontend
pnpm install
pnpm dev
```

- Backend: <http://localhost:8080>
- Frontend: <http://localhost:5173>
- Sign in via the magic link the backend prints to its console (no real email needed in dev when `RESEND_API_KEY` is unset).

### Run the tests

```bash
cd backend && ./mvnw test          # 157 tests, ~3min incl. Testcontainers Postgres
cd frontend && pnpm build          # type-check + production build
cd frontend && pnpm lint           # ESLint (React 19 rules)
```

## Project layout

```
last-minute/
├── backend/                 Spring Boot 3 service (one pom.xml, single deployable jar)
├── frontend/                Vite + React SPA
├── docs/
│   └── superpowers/
│       ├── specs/           Product + system design spec
│       ├── plans/           Per-milestone implementation plans
│       └── retros/          Sprint retrospectives
├── .github/workflows/ci.yml CI: backend (Maven verify) + frontend (tsc/build)
└── README.md
```

## Key design decisions

A few choices that recur in the codebase — context for new contributors:

- **All timestamps `TIMESTAMPTZ` and UTC.** Times are passed from JS via an injectable `Clock` bean (`backend/src/main/java/com/lastminute/common/ClockConfig.java`); raw `Instant.now()` outside the clock service fails an ArchUnit test (see `backend/src/test/java/com/lastminute/archunit/ClockUsageTest.java`).
- **Money is integer cents only.** Commission math is `Math.floorDiv((amount * 15) / 100)` in a single `PricingService`; tests cover the §3.2 boundary set including `99999999999 → 14999999999`.
- **Concurrency:** booking transactions use `SELECT … FOR UPDATE` on the listing row so two simultaneous bookings on the last spot serialize safely; partial unique indices encode "one active booking per consumer per listing" and "redemption-code uniqueness per provider while active." A test fires N=10 concurrent reservers at a capacity-1 listing and asserts exactly one winner.
- **Webhook idempotency:** every Stripe event is persisted to `payment_events` with `UNIQUE (stripe_event_id)`; duplicate deliveries are no-ops. A fast-path `findByStripeEventId` + `saveAndFlush` defeats JPA defer-flush hiding `DataIntegrityViolationException` from the controller. A `webhook_dead_letter` table + a `@Scheduled` drain job handles transient delivery failures.
- **Eventing:** Spring `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)` for `BookingConfirmedHandler`, `RefundHandler`, `ChargebackHandler`, and `RefundRequestAutoCloser` — the Spring-native replacement for Inngest. Email send on first-confirm only (replay-safe via the state machine's `alreadyApplied` branch).
- **Cache:** Caffeine 15-second TTL on listings reads with `@CacheEvict allEntries` on provider publish/edit + admin writes. The correctness layer is the SQL `listing_expires_at > now()` filter, not the cache.
- **JPQL enum literals:** every `@Query` that compares against an enum binds the value via `@Param` instead of writing `b.status = com.lastminute.bookings.BookingStatus.confirmed` — Hibernate emits `'confirmed'::BookingStatus` (the Java FQN) for the literal form, which Postgres doesn't recognize. The retros call this trap out; it bit M2, M4, and M5.

Full design discussion: [`docs/superpowers/specs/2026-05-26-last-minute-booking-design.md`](docs/superpowers/specs/2026-05-26-last-minute-booking-design.md).

## Contributing

This is a solo MVP for now. If you're reading this and want to help, open an issue first to discuss scope — the milestones in the table above are the rough roadmap.

## License

[MIT](LICENSE). Use it, fork it, ship it.
