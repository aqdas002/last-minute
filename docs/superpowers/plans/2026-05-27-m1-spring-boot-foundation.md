# M1 — Spring Boot Foundation + Auth + Anonymous Browse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Stack pivot context:** This plan supersedes `2026-05-26-m1-foundation-auth-browse.md` (Next.js). The Next.js work is archived in git as the `nextjs-attempt` tag/branch. See spec §0 for the pivot rationale.

> **Plan philosophy:** This plan trusts the executor is a working Java developer. It defines file structure, JPA entity shapes, Spring config skeletons, and acceptance criteria — but does not copy-paste every line of idiomatic Spring code. Where the Next.js plan had 30+ pages of copy-pasta, this plan stays focused on shape, decisions, and tests.

**Goal:** A deployed Spring Boot 3 + React (Vite) monorepo where anyone can sign in (magic link or Google) and browse a "starting soon near you" feed of admin-seeded listings. No bookings, no Stripe, no provider self-onboarding.

**Architecture:** Spring Boot 3 monolith (Java 21) serving JSON to a separate React + Vite SPA. Postgres via JPA/Hibernate with Flyway migrations. Spring Security with Spring Session JDBC (sessions stored in Postgres — no Redis). All time goes through a Spring `Clock` bean; all money math through a single `PricingService`.

**Tech Stack:** Java 21 · Spring Boot 3 (latest stable) · Maven · Spring Data JPA · Flyway · Postgres (Neon) · Spring Security 6 · Spring Session JDBC · Caffeine cache · Resend (REST) · Sentry · JUnit 5 · AssertJ · Testcontainers · MockMvc · WireMock · ArchUnit · Vite · React 19 · React Router 7 · TanStack Query · Tailwind v4 · Playwright (E2E).

**Canonical references:**
- Spec: `docs/superpowers/specs/2026-05-26-last-minute-booking-design.md`
- Milestone scope reference: `.brainstorm-draft/implementation-plan-draft.md` (M1 section — written for Next.js; translate concepts to Spring)

---

## Prerequisites (one-time, before Task 1)

The executor must have:
- **Java 21 LTS** (`java -version` shows 21). Install via SDKMAN: `curl -s "https://get.sdkman.io" | bash && sdk install java 21.0.5-tem`
- **Maven 3.9+** (`mvn -version`). Install via SDKMAN: `sdk install maven`
- **Node.js 20+** with `pnpm` (already present from the Next.js attempt)
- **Docker** (for Testcontainers; verify `docker run hello-world` works)
- **Stripe CLI** (for dev webhook testing — used in M3)

---

## Repository shape

```
last-minute/
├── docs/                            # spec + plans (existing)
├── .github/workflows/ci.yml         # monorepo CI
├── backend/                         # Spring Boot
│   ├── pom.xml
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/lastminute/
│   │   │   │   ├── LastMinuteApplication.java
│   │   │   │   ├── common/
│   │   │   │   │   ├── ClockConfig.java
│   │   │   │   │   ├── CacheConfig.java
│   │   │   │   │   ├── WebConfig.java          # CORS
│   │   │   │   │   ├── SentryConfig.java
│   │   │   │   │   └── ApiError.java
│   │   │   │   ├── pricing/
│   │   │   │   │   └── PricingService.java
│   │   │   │   ├── auth/
│   │   │   │   │   ├── SecurityConfig.java
│   │   │   │   │   ├── MagicLinkService.java
│   │   │   │   │   ├── MagicLinkController.java
│   │   │   │   │   ├── ReturnToValidator.java
│   │   │   │   │   ├── CurrentUser.java
│   │   │   │   │   └── ResendClient.java
│   │   │   │   ├── users/
│   │   │   │   │   ├── User.java                # @Entity
│   │   │   │   │   ├── UserRole.java            # enum
│   │   │   │   │   └── UserRepository.java
│   │   │   │   ├── providers/
│   │   │   │   │   ├── Provider.java
│   │   │   │   │   ├── ProviderStatus.java
│   │   │   │   │   └── ProviderRepository.java
│   │   │   │   ├── categories/
│   │   │   │   │   ├── Category.java
│   │   │   │   │   ├── CategoryRepository.java
│   │   │   │   │   └── CategoryController.java
│   │   │   │   ├── listings/
│   │   │   │   │   ├── Listing.java
│   │   │   │   │   ├── ListingStatus.java
│   │   │   │   │   ├── ListingRepository.java
│   │   │   │   │   ├── ListingQueryService.java # enforces expiry filter
│   │   │   │   │   └── ListingController.java
│   │   │   │   └── admin/
│   │   │   │       └── AdminController.java
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       ├── application-test.yml
│   │   │       └── db/migration/
│   │   │           ├── V1__init.sql
│   │   │           └── V2__constraints.sql
│   │   └── test/
│   │       ├── java/com/lastminute/
│   │       │   ├── pricing/PricingServiceTest.java
│   │       │   ├── auth/ReturnToValidatorTest.java
│   │       │   ├── auth/MagicLinkServiceTest.java
│   │       │   ├── listings/ListingQueryServiceTest.java
│   │       │   ├── admin/AdminControllerTest.java
│   │       │   ├── archunit/ClockUsageTest.java       # forbids Instant.now() outside clock bean
│   │       │   └── support/
│   │       │       ├── IntegrationTestBase.java       # @SpringBootTest + Testcontainers
│   │       │       ├── PostgresTestcontainer.java
│   │       │       └── factories/                     # User/Category/Provider/Listing factories
│   │       └── resources/
│   │           └── application-test.yml
│   └── Dockerfile
├── frontend/                         # Vite + React
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── tailwind.config.ts
│   ├── postcss.config.cjs
│   ├── index.html
│   ├── src/
│   │   ├── main.tsx
│   │   ├── App.tsx
│   │   ├── routes.tsx
│   │   ├── api/
│   │   │   ├── client.ts                          # fetch wrapper with credentials: 'include'
│   │   │   ├── listings.ts
│   │   │   └── auth.ts
│   │   ├── pages/
│   │   │   ├── home.tsx                            # hero + starting-soon feed
│   │   │   ├── category.tsx
│   │   │   ├── listing.tsx
│   │   │   ├── signin.tsx
│   │   │   └── admin/
│   │   │       ├── index.tsx
│   │   │       ├── categories.tsx
│   │   │       ├── providers.tsx
│   │   │       └── listings.tsx
│   │   ├── components/
│   │   │   ├── header.tsx
│   │   │   ├── listing-card.tsx
│   │   │   ├── relative-time.tsx
│   │   │   ├── empty-state.tsx
│   │   │   └── loading.tsx
│   │   ├── lib/
│   │   │   ├── return-to.ts                        # MIRRORS backend regex; single source: tests verify parity
│   │   │   └── format.ts
│   │   └── styles/
│   │       └── globals.css
│   └── tests/
│       └── return-to.test.ts
└── README.md
```

---

## Task 1 — Wipe Next.js scaffold; init monorepo skeleton

**Files:** delete Next.js artefacts; create `backend/` + `frontend/` empty directories + monorepo `README.md`.

- [ ] **Step 1: Verify the Next.js attempt is preserved**

```bash
git tag -l | grep nextjs-attempt    # expect: nextjs-attempt
git branch | grep nextjs-attempt    # expect: nextjs-attempt
```

If absent, halt and tell the orchestrator. Otherwise proceed.

- [ ] **Step 2: Remove Next.js files from `main`**

```bash
git rm -rf src/ prisma/ eslint-rules/ public/ \
           .env.example .env.local \
           .prettierrc.json .prettierignore \
           eslint.config.mjs next.config.ts next-env.d.ts \
           package.json pnpm-lock.yaml postcss.config.mjs \
           tsconfig.json vitest.config.ts vitest.setup.ts \
           2>/dev/null || true
rm -rf node_modules .next
```

Keep: `docs/`, `.brainstorm-draft/`, `.superpowers/`, `.claude/`, `.git/`, `.gitignore`.

- [ ] **Step 3: Replace `.gitignore`**

```
# IDEs
.idea/
.vscode/
*.iml

# Java / Maven
backend/target/
backend/.mvn/wrapper/maven-wrapper.jar
backend/HELP.md

# Node / Vite
frontend/node_modules/
frontend/dist/
frontend/.vite/

# OS
.DS_Store
Thumbs.db

# Env
.env*
!.env.example

# Logs
*.log

# Testcontainers / Docker artifacts
.testcontainers/
```

- [ ] **Step 4: Create monorepo `README.md`**

```markdown
# Last Minute

Last-minute booking marketplace (MVP). Spring Boot 3 backend + React (Vite) SPA, Postgres.

## Quick start

```bash
# Backend
cd backend
cp src/main/resources/application.yml.example src/main/resources/application-local.yml
./mvnw spring-boot:run

# Frontend (new terminal)
cd frontend
pnpm install
pnpm dev
```

Backend: http://localhost:8080 · Frontend: http://localhost:5173.

## Docs

- Spec: `docs/superpowers/specs/2026-05-26-last-minute-booking-design.md`
- M1 plan: `docs/superpowers/plans/2026-05-27-m1-spring-boot-foundation.md`
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore: archive Next.js attempt; reset main for Spring Boot pivot"
```

---

## Task 2 — Generate Spring Boot backend via Spring Initializr

**Files:** `backend/pom.xml`, `backend/mvnw`, `backend/src/main/java/com/lastminute/LastMinuteApplication.java`, `backend/src/main/resources/application.yml`, `backend/Dockerfile`.

- [ ] **Step 1: Generate via Spring Initializr CLI**

```bash
cd /mnt/c/Users/aqdas/Downloads/last-minute

curl -s https://start.spring.io/starter.zip \
  -d type=maven-project \
  -d language=java \
  -d bootVersion=3.4.0 \
  -d baseDir=backend \
  -d groupId=com.lastminute \
  -d artifactId=last-minute \
  -d name=last-minute \
  -d packageName=com.lastminute \
  -d javaVersion=21 \
  -d dependencies=web,data-jpa,validation,security,oauth2-client,session,flyway,postgresql,actuator,cache,testcontainers \
  -o backend-init.zip

unzip backend-init.zip
rm backend-init.zip
```

(Adjust `bootVersion` to the current stable on start.spring.io if 3.4.0 isn't latest.)

- [ ] **Step 2: Add extra dependencies to `backend/pom.xml`**

Append to `<dependencies>`:

```xml
<!-- Spring Session backed by JDBC (Postgres) -->
<dependency>
  <groupId>org.springframework.session</groupId>
  <artifactId>spring-session-jdbc</artifactId>
</dependency>

<!-- Caffeine cache -->
<dependency>
  <groupId>com.github.ben-manes.caffeine</groupId>
  <artifactId>caffeine</artifactId>
</dependency>

<!-- Sentry -->
<dependency>
  <groupId>io.sentry</groupId>
  <artifactId>sentry-spring-boot-starter-jakarta</artifactId>
  <version>7.18.0</version>
</dependency>

<!-- Resend (use their official Java SDK, or call REST via WebClient) -->
<dependency>
  <groupId>com.resend</groupId>
  <artifactId>resend-java</artifactId>
  <version>4.0.0</version>
</dependency>

<!-- Testing extras -->
<dependency>
  <groupId>com.github.tomakehurst</groupId>
  <artifactId>wiremock-jre8-standalone</artifactId>
  <version>3.0.1</version>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.awaitility</groupId>
  <artifactId>awaitility</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>com.tngtech.archunit</groupId>
  <artifactId>archunit-junit5</artifactId>
  <version>1.3.0</version>
  <scope>test</scope>
</dependency>
```

- [ ] **Step 3: Configure `backend/src/main/resources/application.yml`**

```yaml
spring:
  application:
    name: last-minute
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/lastminute}
    username: ${DATABASE_USER:lastminute}
    password: ${DATABASE_PASSWORD:lastminute}
  jpa:
    hibernate:
      ddl-auto: validate                 # Flyway owns schema
    open-in-view: false                  # avoid OSIV foot-gun
    properties:
      hibernate.format_sql: true
  flyway:
    enabled: true
  session:
    store-type: jdbc
    jdbc:
      initialize-schema: always
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${AUTH_GOOGLE_ID:}
            client-secret: ${AUTH_GOOGLE_SECRET:}
            scope: openid,profile,email
server:
  port: 8080
  forward-headers-strategy: framework   # behind Fly.io proxy

app:
  base-url: ${APP_URL:http://localhost:8080}
  frontend-origin: ${FRONTEND_ORIGIN:http://localhost:5173}
  resend:
    api-key: ${RESEND_API_KEY:}
    from: ${EMAIL_FROM:dev@local}
  commission-percent: 15

sentry:
  dsn: ${SENTRY_DSN:}
  environment: ${SENTRY_ENVIRONMENT:development}
  traces-sample-rate: 0.1
  enabled: ${SENTRY_DSN:false}            # disabled when DSN absent
```

- [ ] **Step 4: Verify the skeleton compiles**

```bash
cd backend
./mvnw -DskipTests package
```

Expected: BUILD SUCCESS with a jar in `target/`.

- [ ] **Step 5: Commit**

```bash
cd ..
git add backend/
git commit -m "feat(backend): Spring Boot 3 + Maven scaffold (Java 21, JPA, Security, Flyway, Postgres)"
```

---

## Task 3 — Flyway V1: initial schema (users, providers, categories, listings + auth tables)

**Files:** `backend/src/main/resources/db/migration/V1__init.sql`, `V2__constraints.sql`.

Schema follows spec §4 verbatim. JPA entities come in Tasks 6–9; the SQL is the source of truth for column types.

- [ ] **Step 1: Write `V1__init.sql`**

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Users
CREATE TYPE user_role AS ENUM ('consumer', 'provider', 'admin');

CREATE TABLE users (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email       TEXT NOT NULL UNIQUE,
  name        TEXT,
  phone       TEXT,
  role        user_role NOT NULL DEFAULT 'consumer',
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Providers (1:1 with users where role = provider)
CREATE TYPE provider_status AS ENUM ('pending_kyc', 'active', 'suspended');

CREATE TABLE providers (
  id                            UUID PRIMARY KEY REFERENCES users(id) ON DELETE RESTRICT,
  business_name                 TEXT NOT NULL,
  business_description          TEXT,
  contact_phone                 TEXT,
  currency                      CHAR(3) NOT NULL,
  timezone                      TEXT NOT NULL,
  stripe_account_id             TEXT UNIQUE,
  stripe_onboarding_complete    BOOLEAN NOT NULL DEFAULT FALSE,
  stripe_charges_enabled        BOOLEAN NOT NULL DEFAULT FALSE,
  stripe_payouts_enabled        BOOLEAN NOT NULL DEFAULT FALSE,
  default_address               TEXT,
  default_lat                   DOUBLE PRECISION,
  default_lon                   DOUBLE PRECISION,
  city                          TEXT,
  country                       TEXT,
  status                        provider_status NOT NULL DEFAULT 'pending_kyc',
  created_at                    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Categories
CREATE TABLE categories (
  id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  slug                     TEXT NOT NULL UNIQUE,
  name                     TEXT NOT NULL,
  icon_name                TEXT,
  parent_id                UUID REFERENCES categories(id) ON DELETE SET NULL,
  display_order            INT NOT NULL DEFAULT 0,
  active                   BOOLEAN NOT NULL DEFAULT TRUE,
  no_show_grace_interval   INTERVAL NOT NULL DEFAULT '2 hours',
  created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Listings
CREATE TYPE listing_status AS ENUM ('draft', 'active', 'sold_out', 'expired', 'cancelled', 'suspended');

CREATE TABLE listings (
  id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  provider_id              UUID NOT NULL REFERENCES providers(id) ON DELETE RESTRICT,
  category_id              UUID NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
  title                    TEXT NOT NULL,
  description              TEXT,
  images                   JSONB NOT NULL DEFAULT '[]'::jsonb,
  original_price_cents     INT NOT NULL,
  discounted_price_cents   INT NOT NULL,
  currency                 CHAR(3) NOT NULL,
  capacity                 INT NOT NULL DEFAULT 1,
  start_time               TIMESTAMPTZ NOT NULL,
  end_time                 TIMESTAMPTZ NOT NULL,
  listing_expires_at       TIMESTAMPTZ NOT NULL,
  timezone                 TEXT NOT NULL,
  address                  TEXT,
  lat                      DOUBLE PRECISION,
  lon                      DOUBLE PRECISION,
  city                     TEXT,
  status                   listing_status NOT NULL DEFAULT 'draft',
  metadata                 JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX listings_browse_idx ON listings (category_id, status, city, listing_expires_at);
CREATE INDEX listings_geo_idx ON listings (city, lat, lon);
CREATE INDEX listings_provider_idx ON listings (provider_id, status);

-- Magic-link verification tokens (Auth.js compatibility carried over for schema clarity)
CREATE TABLE verification_tokens (
  identifier  TEXT NOT NULL,
  token       TEXT NOT NULL UNIQUE,
  expires     TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (identifier, token)
);
```

The `note:` Spring Session JDBC manages its own session tables (`SPRING_SESSION` etc.) via `spring.session.jdbc.initialize-schema: always`; we do NOT define them here.

- [ ] **Step 2: Write `V2__constraints.sql`**

```sql
ALTER TABLE listings
  ADD CONSTRAINT listings_capacity_ck            CHECK (capacity >= 1),
  ADD CONSTRAINT listings_prices_ck              CHECK (discounted_price_cents > 0 AND discounted_price_cents < original_price_cents),
  ADD CONSTRAINT listings_end_after_start_ck     CHECK (end_time > start_time),
  ADD CONSTRAINT listings_expires_before_end_ck  CHECK (listing_expires_at <= end_time);
```

- [ ] **Step 3: Local DB for dev**

```bash
docker run --name lastminute-pg -e POSTGRES_USER=lastminute -e POSTGRES_PASSWORD=lastminute -e POSTGRES_DB=lastminute -p 5432:5432 -d postgres:16-alpine
```

- [ ] **Step 4: Run migrations and verify**

```bash
cd backend
./mvnw spring-boot:run
# In another terminal:
psql postgresql://lastminute:lastminute@localhost:5432/lastminute -c "\dt"
```

Expected: all 5 business tables + Spring Session tables present.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration
git commit -m "feat(db): Flyway V1 init schema + V2 constraints per spec §4"
```

---

## Task 4 — `ClockConfig` + `Clock` bean + ArchUnit test forbidding raw `Instant.now()`

**Files:** `backend/src/main/java/com/lastminute/common/ClockConfig.java`, `backend/src/test/java/com/lastminute/archunit/ClockUsageTest.java`.

- [ ] **Step 1: Implement `ClockConfig`**

```java
package com.lastminute.common;

import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

  /** Default UTC clock for production. Tests override with `Clock.fixed(...)` via @MockBean / @TestConfiguration. */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
```

Inject `Clock` everywhere business logic needs "now" (e.g. `Instant.now(clock)`).

- [ ] **Step 2: ArchUnit test forbidding raw `Instant.now()`/`LocalDateTime.now()`/`OffsetDateTime.now()`**

```java
package com.lastminute.archunit;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.lastminute")
public class ClockUsageTest {

  @ArchTest
  static final ArchRule noRawInstantNow =
      noClasses()
          .that().resideOutsideOfPackages("com.lastminute.common..", "..test..")
          .should().callMethod(java.time.Instant.class, "now")
          .orShould().callMethod(java.time.LocalDateTime.class, "now")
          .orShould().callMethod(java.time.OffsetDateTime.class, "now")
          .because("use injected Clock: Instant.now(clock). See spec §3.1.");
}
```

This catches the same problem class as the Next.js plan's ESLint `no-raw-date` rule.

- [ ] **Step 3: Test that the rule fires**

Drop a deliberate violation in `backend/src/main/java/com/lastminute/__verify/RawNowViolation.java`:

```java
package com.lastminute.__verify;
import java.time.Instant;
public class RawNowViolation { static Instant t = Instant.now(); }
```

Run `./mvnw test -Dtest=ClockUsageTest`. Expected: FAIL on the violation. Delete the file, re-run. Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/lastminute/common/ClockConfig.java \
        backend/src/test/java/com/lastminute/archunit/
git commit -m "feat(clock): injectable Clock bean + ArchUnit guard against raw Instant.now()"
```

---

## Task 5 — `PricingService` with §3.2 boundary tests

**Files:** `backend/src/main/java/com/lastminute/pricing/PricingService.java`, `backend/src/test/java/com/lastminute/pricing/PricingServiceTest.java`.

- [ ] **Step 1: Failing test (full §3.2 boundary set)**

```java
package com.lastminute.pricing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class PricingServiceTest {

  private final PricingService p = new PricingService(15);

  @ParameterizedTest
  @CsvSource({
      "1, 0",
      "7, 1",
      "100, 15",
      "333, 49",
      "999, 149",
      "99999999999, 14999999999",
  })
  void floor_div_15(long amountCents, long expected) {
    assertThat(p.platformFeeCents(amountCents)).isEqualTo(expected);
  }

  @Test void zero_is_zero()      { assertThat(p.platformFeeCents(0)).isEqualTo(0); }
  @Test void negative_throws()   { assertThatThrownBy(() -> p.platformFeeCents(-1)).isInstanceOf(IllegalArgumentException.class); }
  @Test void provider_payout()   { assertThat(p.providerPayoutCents(100)).isEqualTo(85); }
}
```

- [ ] **Step 2: Implement**

```java
package com.lastminute.pricing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PricingService {

  private final int commissionPercent;

  public PricingService(@Value("${app.commission-percent:15}") int commissionPercent) {
    if (commissionPercent < 0 || commissionPercent >= 100) {
      throw new IllegalArgumentException("commissionPercent must be in [0, 100); got " + commissionPercent);
    }
    this.commissionPercent = commissionPercent;
  }

  public long platformFeeCents(long amountCents) {
    if (amountCents < 0) throw new IllegalArgumentException("amountCents must be non-negative; got " + amountCents);
    return Math.floorDiv(amountCents * commissionPercent, 100L);
  }

  public long providerPayoutCents(long amountCents) {
    return amountCents - platformFeeCents(amountCents);
  }
}
```

(`Math.floorDiv` uses integer arithmetic; the multiplication is `long`-safe for values up to ~6e17 with `commissionPercent <= 15`.)

- [ ] **Step 3: Run and commit**

```bash
cd backend
./mvnw test -Dtest=PricingServiceTest
git add src/main/java/com/lastminute/pricing src/test/java/com/lastminute/pricing
git commit -m "feat(pricing): integer-only commission math with §3.2 boundary tests"
```

---

## Task 6 — `ReturnToValidator` (single source of truth for return-to allowlist)

**Files:** `backend/src/main/java/com/lastminute/auth/ReturnToValidator.java`, `backend/src/test/java/com/lastminute/auth/ReturnToValidatorTest.java`.

- [ ] **Step 1: Test (full allow + deny matrix per Next.js M1 plan Task 10)**

```java
package com.lastminute.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class ReturnToValidatorTest {

  private final ReturnToValidator v = new ReturnToValidator();

  @ParameterizedTest
  @ValueSource(strings = {
      "/", "/c/yoga", "/c/restaurants-and-bars",
      "/c/yoga?filter=tonight", "/c/yoga?filter=tonight&distance=5",
      "/bookings/abc12345-6789-4def-9012-3456789abcde",
      "/bookings/abc12345-6789-4def-9012-3456789abcde?from=email",
      "/book/abc12345-6789-4def-9012-3456789abcde",
      "/provider/dashboard", "/provider/onboarding",
      "/provider/bookings", "/provider/listings",
      "/provider/dashboard/settings",
      "/provider/listings?status=draft",
  })
  void allowed(String p) { assertThat(v.isAllowed(p)).isTrue(); }

  @ParameterizedTest
  @ValueSource(strings = {
      "https://evil.com", "http://evil.com", "//evil.com", "//evil.com/foo",
      "javascript:alert(1)", "data:text/html,<script>", "vbscript:msgbox(1)",
      "/c/../admin", "/c/%2e%2e/admin", "/bookings/%2F..%2Fadmin",
      "/\\evil", "\\evil", "", " ",
      "/admin", "/admin/users", "/api/secret", "/foo",
      "/bookings/not-a-uuid", "/bookings/abc.com",
      "/provider/secret", "/provider/admin-thing",
      "#foo", "///",
  })
  void denied(String p) { assertThat(v.isAllowed(p)).isFalse(); }

  @Test void safe_returns_slash_for_denied() {
    assertThat(v.safe("https://evil.com")).isEqualTo("/");
    assertThat(v.safe(null)).isEqualTo("/");
  }
}
```

- [ ] **Step 2: Implement (regex mirrors the spec §3.3 single-source pattern)**

```java
package com.lastminute.auth;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ReturnToValidator {

  // Mirror of the frontend regex; frontend has a parity test verifying they match.
  private static final Pattern ALLOW = Pattern.compile(
      "^/(?:$|c/[a-z0-9-]+(?:\\?[^#]*)?$"
    + "|bookings/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?:\\?[^#]*)?$"
    + "|book/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    + "|provider/(?:dashboard|onboarding|bookings|listings)(?:/[a-z0-9-]+)?(?:\\?[^#]*)?$)"
  );

  public boolean isAllowed(String p) {
    if (p == null || p.isEmpty()) return false;
    if (p.contains("\\")) return false;
    if (p.startsWith("//")) return false;
    return ALLOW.matcher(p).matches();
  }

  public String safe(String p) {
    return isAllowed(p) ? p : "/";
  }
}
```

- [ ] **Step 3: Run and commit**

```bash
./mvnw test -Dtest=ReturnToValidatorTest
git add backend/src/main/java/com/lastminute/auth/ReturnToValidator.java \
        backend/src/test/java/com/lastminute/auth/ReturnToValidatorTest.java
git commit -m "feat(auth): return-to allowlist (single source of truth) + deny-vector tests"
```

---

## Task 7 — JPA entities: `User`, `Provider`, `Category`, `Listing` + repositories

**Files:** entity classes + Spring Data JPA repository interfaces, in their respective packages.

- [ ] **Step 1: Entities**

Pattern (apply to all four):

```java
// users/User.java
package com.lastminute.users;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
public class User {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true) private String email;
  @Column private String name;
  @Column private String phone;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(columnDefinition = "user_role", nullable = false)
  private UserRole role = UserRole.consumer;

  @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
  @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

  @PrePersist void onCreate() {
    OffsetDateTime now = OffsetDateTime.now();   // EXEMPTED in ClockUsageTest via package allowlist note
    createdAt = updatedAt = now;
  }
  @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(); }

  // getters/setters
}
```

(Auditing via `@PrePersist`/`@PreUpdate` uses raw `OffsetDateTime.now()` — exempt this *only* in `@PrePersist`/`@PreUpdate` JPA callbacks. Update ArchUnit rule's allowlist to include `*..users.User.onCreate`, etc., OR refactor to use Hibernate's `@CreationTimestamp` / `@UpdateTimestamp` which delegate to the DB clock (matches §3.1 acceptance for audit-only timestamps).)

Recommendation: **use `@CreationTimestamp` + `@UpdateTimestamp`** to satisfy ArchUnit cleanly:

```java
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@CreationTimestamp @Column(name = "created_at", updatable = false) private OffsetDateTime createdAt;
@UpdateTimestamp   @Column(name = "updated_at") private OffsetDateTime updatedAt;
```

Apply the same pattern to all four entities.

Entity fields mirror the Flyway SQL from Task 3 verbatim. Use `@Enumerated(EnumType.STRING) + @JdbcTypeCode(SqlTypes.NAMED_ENUM)` to map Postgres enums. Use `@JdbcTypeCode(SqlTypes.JSON)` on `images` and `metadata` (`Map<String, Object>` or `List<String>`).

Place enums alongside entities (`UserRole.java`, `ProviderStatus.java`, `ListingStatus.java`) with values matching the SQL enum types exactly (lowercase + underscore).

- [ ] **Step 2: Repositories**

```java
// users/UserRepository.java
package com.lastminute.users;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByEmail(String email);
}
```

Similar interfaces for `ProviderRepository`, `CategoryRepository`, `ListingRepository`. Add custom finders only as needed by later tasks.

- [ ] **Step 3: Integration test — verify schema-entity parity**

```java
// users/UserEntityIT.java
package com.lastminute.users;

import com.lastminute.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

class UserEntityIT extends IntegrationTestBase {

  @Autowired UserRepository repo;

  @Test void persist_round_trip() {
    User u = new User();
    u.setEmail("rt@test.local");
    u.setRole(UserRole.consumer);
    User saved = repo.save(u);
    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getRole()).isEqualTo(UserRole.consumer);
    assertThat(repo.findByEmail("rt@test.local")).isPresent();
  }
}
```

(`IntegrationTestBase` lands in Task 9.)

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/lastminute/{users,providers,categories,listings}/
git commit -m "feat(jpa): User/Provider/Category/Listing entities + repositories"
```

---

## Task 8 — Testcontainers integration-test harness (`IntegrationTestBase`)

**Files:** `backend/src/test/java/com/lastminute/support/IntegrationTestBase.java`, `backend/src/test/resources/application-test.yml`.

- [ ] **Step 1: Implement**

```java
package com.lastminute.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class IntegrationTestBase {

  @Container
  @ServiceConnection                  // wires Spring's DataSource to the container automatically
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine").withReuse(true);
}
```

`withReuse(true)` plus `testcontainers.reuse.enable=true` in `~/.testcontainers.properties` makes the container persist across runs (huge dev-loop win; CI sets `withReuse(false)`).

- [ ] **Step 2: `application-test.yml`**

```yaml
spring:
  flyway:
    enabled: true
  jpa:
    hibernate:
      ddl-auto: validate
sentry:
  enabled: false
app:
  resend:
    api-key: ""
```

- [ ] **Step 3: Truncate-between-tests helper**

```java
// support/DbTruncate.java
package com.lastminute.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DbTruncate {
  @PersistenceContext EntityManager em;

  @Transactional
  public void truncateAll() {
    em.createNativeQuery("""
      TRUNCATE TABLE verification_tokens, listings, providers, categories, users
      RESTART IDENTITY CASCADE
    """).executeUpdate();
  }
}
```

Add `@BeforeEach` in `IntegrationTestBase` that injects and calls `truncateAll()`.

- [ ] **Step 4: Run the User round-trip test from Task 7**

```bash
./mvnw test -Dtest=UserEntityIT
```

Expected: PASS (Testcontainers pulls Postgres image, runs Flyway, executes the round trip).

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/lastminute/support backend/src/test/resources
git commit -m "test: Testcontainers per-class Postgres + truncate harness + base class"
```

---

## Task 9 — `MagicLinkService` + magic-link controller endpoints

**Files:** `MagicLinkService.java`, `MagicLinkController.java`, `ResendClient.java`, `VerificationToken.java` (+ repository), unit + integration tests.

- [ ] **Step 1: `VerificationToken` entity + repository**

JPA entity with composite key `(identifier, token)`. Use `@IdClass(VerificationTokenId.class)`.

```java
@Entity @Table(name = "verification_tokens")
@IdClass(VerificationTokenId.class)
public class VerificationToken {
  @Id private String identifier;
  @Id @Column(unique = true) private String token;
  @Column(nullable = false) private OffsetDateTime expires;
  // getters/setters
}

public record VerificationTokenId(String identifier, String token) implements Serializable {}
```

```java
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, VerificationTokenId> {
  Optional<VerificationToken> findByToken(String token);
}
```

- [ ] **Step 2: `ResendClient` (REST wrapper)**

```java
@Component
public class ResendClient {
  private final String apiKey;
  private final String from;
  private final RestClient http;

  public ResendClient(@Value("${app.resend.api-key:}") String apiKey,
                      @Value("${app.resend.from:dev@local}") String from) {
    this.apiKey = apiKey;
    this.from = from;
    this.http = RestClient.builder().baseUrl("https://api.resend.com").build();
  }

  public void sendMagicLink(String to, String url) {
    if (apiKey.isBlank()) { /* dev mode: log instead of send */ return; }
    http.post().uri("/emails")
        .header("Authorization", "Bearer " + apiKey)
        .body(Map.of("from", from, "to", to, "subject", "Your sign-in link",
                     "html", "<p><a href=\"" + url + "\">Sign in to Last Minute</a></p>"))
        .retrieve().toBodilessEntity();
  }
}
```

(WireMock test in Task 13 verifies the call shape.)

- [ ] **Step 3: `MagicLinkService`**

```java
@Service
public class MagicLinkService {
  private static final SecureRandom RNG = new SecureRandom();
  private final VerificationTokenRepository tokens;
  private final ResendClient email;
  private final Clock clock;
  private final String baseUrl;

  public MagicLinkService(VerificationTokenRepository tokens, ResendClient email, Clock clock,
                          @Value("${app.base-url}") String baseUrl) {
    this.tokens = tokens; this.email = email; this.clock = clock; this.baseUrl = baseUrl;
  }

  @Transactional
  public void request(String email) {
    String token = randomToken();
    OffsetDateTime expires = OffsetDateTime.ofInstant(Instant.now(clock).plus(Duration.ofMinutes(15)), ZoneOffset.UTC);
    VerificationToken vt = new VerificationToken();
    vt.setIdentifier(email); vt.setToken(token); vt.setExpires(expires);
    tokens.save(vt);
    this.email.sendMagicLink(email, baseUrl + "/api/auth/magic?token=" + token);
  }

  /** Consume on callback. Throws if invalid/expired. */
  @Transactional
  public String consume(String token) {
    VerificationToken vt = tokens.findByToken(token).orElseThrow(() -> new InvalidTokenException("not_found"));
    if (vt.getExpires().toInstant().isBefore(Instant.now(clock))) {
      tokens.delete(vt);
      throw new InvalidTokenException("expired");
    }
    String identifier = vt.getIdentifier();
    tokens.delete(vt);                  // single-use
    return identifier;
  }

  private static String randomToken() {
    byte[] buf = new byte[32]; RNG.nextBytes(buf);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
  }
}

class InvalidTokenException extends RuntimeException { InvalidTokenException(String s) { super(s); } }
```

- [ ] **Step 4: `MagicLinkController` + `OAuth2 (Google)` config in `SecurityConfig`**

```java
@RestController
@RequestMapping("/api/auth")
public class MagicLinkController {
  private final MagicLinkService magic;
  private final ReturnToValidator returnTo;
  private final UserRepository users;

  public MagicLinkController(MagicLinkService magic, ReturnToValidator returnTo, UserRepository users) {
    this.magic = magic; this.returnTo = returnTo; this.users = users;
  }

  @PostMapping("/magic/request")
  public ResponseEntity<Void> request(@RequestBody MagicLinkRequest body) {
    magic.request(body.email());
    return ResponseEntity.accepted().build();
  }

  @GetMapping("/magic")
  public ResponseEntity<Void> consume(@RequestParam String token,
                                      @RequestParam(required = false) String return_to,
                                      HttpServletRequest req) {
    try {
      String email = magic.consume(token);
      User u = users.findByEmail(email).orElseGet(() -> {
        User n = new User(); n.setEmail(email); n.setRole(UserRole.consumer);
        return users.save(n);
      });

      // Mint Spring Security session
      var auth = new UsernamePasswordAuthenticationToken(
          new CurrentUser(u.getId(), u.getEmail(), u.getRole()), null,
          List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name().toUpperCase())));
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(auth);
      SecurityContextHolder.setContext(context);
      req.getSession(true).setAttribute(
          HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

      String target = returnTo.safe(return_to);
      return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(frontend(target))).build();
    } catch (InvalidTokenException e) {
      return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(frontend("/signin?error=" + e.getMessage()))).build();
    }
  }

  private String frontend(String path) { return /* env: app.frontend-origin */ path; }
  public record MagicLinkRequest(String email) {}
}
```

`CurrentUser` is a record with `id, email, role`. Inject into controllers as `@AuthenticationPrincipal CurrentUser user`.

- [ ] **Step 5: `SecurityConfig`**

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain chain(HttpSecurity http, @Value("${app.frontend-origin}") String frontendOrigin) throws Exception {
    http
      .csrf(c -> c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                 .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
      .authorizeHttpRequests(a -> a
        .requestMatchers("/api/auth/**", "/api/listings/**", "/api/categories/**", "/actuator/health").permitAll()
        .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
        .anyRequest().authenticated())
      .oauth2Login(o -> o.successHandler(new OAuthSuccessHandler(/*UserRepository, ReturnToValidator*/)))
      .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
      .cors(Customizer.withDefaults());
    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(@Value("${app.frontend-origin}") String origin) {
    var cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(List.of(origin));
    cfg.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
    cfg.setAllowedHeaders(List.of("*"));
    cfg.setAllowCredentials(true);
    var src = new UrlBasedCorsConfigurationSource();
    src.registerCorsConfiguration("/**", cfg);
    return src;
  }
}
```

`SpaCsrfTokenRequestHandler` is Spring's recommended SPA pattern — reads XSRF-TOKEN cookie and matches header `X-XSRF-TOKEN`. Standard Spring Security 6 docs cover the pattern.

`OAuthSuccessHandler` looks up or creates the user by email, sets `role=consumer` on first login, and redirects to `returnTo.safe(...)`.

- [ ] **Step 6: Tests**

- `MagicLinkServiceTest` (unit, with `Clock.fixed(...)` and a mocked `ResendClient`): request creates a token, consume succeeds before expiry, consume fails after expiry, single-use enforced (second consume throws).
- `MagicLinkControllerIT` (integration with MockMvc): POST `/api/auth/magic/request` → 202 + token row created. GET `/api/auth/magic?token=...` → 302 to safe return_to, session cookie set, `users` row with `role=consumer` exists.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/lastminute/auth backend/src/test/java/com/lastminute/auth
git commit -m "feat(auth): MagicLink request/consume + Spring Security + CSRF + first-login user creation"
```

---

## Task 10 — `ListingQueryService` + `ListingController` + `CategoryController` (belt-and-braces expiry filter)

**Files:** services, controllers, integration tests.

- [ ] **Step 1: `ListingQueryService` (every consumer query filters `listing_expires_at > clock.now()`)**

```java
@Service
public class ListingQueryService {
  private final ListingRepository repo;
  private final Clock clock;
  public ListingQueryService(ListingRepository repo, Clock clock) {
    this.repo = repo; this.clock = clock;
  }

  public List<Listing> startingSoon(@Nullable String city) {
    Instant now = Instant.now(clock);
    return repo.findStartingSoon(now, city);
  }

  public List<Listing> byCategorySlug(String slug) {
    Instant now = Instant.now(clock);
    return repo.findActiveByCategorySlug(now, slug);
  }

  public Optional<Listing> byId(UUID id) {
    Instant now = Instant.now(clock);
    return repo.findActiveById(now, id);
  }
}
```

`ListingRepository` adds `@Query` methods with the filter baked in:

```java
public interface ListingRepository extends JpaRepository<Listing, UUID> {
  @Query("""
    SELECT l FROM Listing l
    WHERE l.status = com.lastminute.listings.ListingStatus.active
      AND l.listingExpiresAt > :now
      AND (:city IS NULL OR l.city = :city)
    ORDER BY l.startTime ASC
  """)
  List<Listing> findStartingSoon(@Param("now") Instant now, @Param("city") String city);

  @Query("""
    SELECT l FROM Listing l JOIN l.category c
    WHERE c.slug = :slug
      AND l.status = com.lastminute.listings.ListingStatus.active
      AND l.listingExpiresAt > :now
    ORDER BY l.startTime ASC, l.discountedPriceCents ASC
  """)
  List<Listing> findActiveByCategorySlug(@Param("now") Instant now, @Param("slug") String slug);

  @Query("""
    SELECT l FROM Listing l
    WHERE l.id = :id AND l.status = com.lastminute.listings.ListingStatus.active
      AND l.listingExpiresAt > :now
  """)
  Optional<Listing> findActiveById(@Param("now") Instant now, @Param("id") UUID id);
}
```

- [ ] **Step 2: Integration test covering the QA-flagged matrix (verbatim from Next.js M1 Task 18 patches)**

`ListingQueryServiceIT.java`: with `Clock.fixed(t0)`:
- excludes past `listing_expires_at`
- excludes status != active (draft/suspended/cancelled/expired/sold_out)
- city filter actually filters
- equality boundary (`listingExpiresAt = now()`) excluded (strict gt)
- `byId` returns empty for expired
- emoji round-trip preserved

- [ ] **Step 3: `ListingController` + `CategoryController`**

```java
@RestController @RequestMapping("/api/listings")
public class ListingController {
  private final ListingQueryService q;
  public ListingController(ListingQueryService q) { this.q = q; }

  @GetMapping
  public List<ListingDto> startingSoon(@RequestParam(required = false) String city) {
    return q.startingSoon(city).stream().map(ListingDto::from).toList();
  }
  @GetMapping("/{id}")
  public ResponseEntity<ListingDto> byId(@PathVariable UUID id) {
    return q.byId(id).map(ListingDto::from).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }
}

@RestController @RequestMapping("/api/categories")
public class CategoryController {
  private final CategoryRepository cats;
  private final ListingQueryService q;
  public CategoryController(CategoryRepository cats, ListingQueryService q) { this.cats = cats; this.q = q; }

  @GetMapping
  public List<CategoryDto> all() { return cats.findAll().stream().filter(Category::isActive).map(CategoryDto::from).toList(); }

  @GetMapping("/{slug}/listings")
  public List<ListingDto> bySlug(@PathVariable String slug) {
    return q.byCategorySlug(slug).stream().map(ListingDto::from).toList();
  }
}
```

DTOs are simple records: `ListingDto`, `CategoryDto`. Don't serialize JPA entities directly.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/lastminute/listings backend/src/main/java/com/lastminute/categories backend/src/test/java/com/lastminute/listings
git commit -m "feat(listings): query service with belt-and-braces expiry filter + controllers"
```

---

## Task 11 — `AdminController` (seed forms — categories, providers, listings)

**Files:** `AdminController.java`, `AdminControllerIT.java`.

Endpoints (all `ROLE_ADMIN` only — enforced in `SecurityConfig` matcher):
- `POST /api/admin/categories` — create category from `{slug, name}`
- `POST /api/admin/providers` — create user + provider row (admin-mediated; no Stripe yet)
- `POST /api/admin/listings` — create listing with admin-provided fields; calls cache eviction on category

Validation: Bean Validation (`@Valid` + `@NotBlank`, `@Min`, `@Positive`) on request DTOs. Reject:
- `capacity < 1`
- `discountedPriceCents < 50`
- `discountedPriceCents >= originalPriceCents`
- `startTime <= Instant.now(clock)`
- `listingExpiresAt > endTime`
- `images.size() < 1 || > 10`

Integration test (`AdminControllerIT`): MockMvc as admin user, POST each endpoint, assert DB rows + cache evicted.

```bash
git add backend/src/main/java/com/lastminute/admin backend/src/test/java/com/lastminute/admin
git commit -m "feat(admin): seed endpoints for categories/providers/listings (M1 dogfooding)"
```

---

## Task 12 — Caching (`CacheConfig` + `@Cacheable` on listings + eviction on admin write)

**Files:** `CacheConfig.java`; sprinkle `@Cacheable` + `@CacheEvict`.

- [ ] **Step 1: `CacheConfig`**

```java
@Configuration
@EnableCaching
public class CacheConfig {
  @Bean
  public CacheManager cacheManager() {
    CaffeineCacheManager mgr = new CaffeineCacheManager("listings-by-category", "starting-soon");
    mgr.setCaffeine(Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(15))
        .maximumSize(1000));
    return mgr;
  }
}
```

- [ ] **Step 2: Annotate `ListingQueryService`**

```java
@Cacheable(cacheNames = "starting-soon", key = "#city == null ? 'ALL' : #city")
public List<Listing> startingSoon(@Nullable String city) { ... }

@Cacheable(cacheNames = "listings-by-category", key = "#slug")
public List<Listing> byCategorySlug(String slug) { ... }
```

- [ ] **Step 3: `AdminController.createListing` evicts after write**

```java
@CacheEvict(cacheNames = {"listings-by-category", "starting-soon"}, allEntries = true)
public ListingDto create(...) { ... }
```

Coarse eviction is fine for M1 (no per-category granularity needed yet).

- [ ] **Step 4: Cache round-trip integration test**

`CacheInvalidateIT.java`: read by category → create listing → assert second read sees the new row (cache evicted). Assert cache TTL (sleep 16s; second read after TTL also returns fresh — slow test, mark `@Tag("slow")` for nightly only).

```bash
git add backend/src/main/java/com/lastminute/common/CacheConfig.java
git commit -m "feat(cache): Caffeine 15s TTL on listing reads + evict on admin write"
```

---

## Task 13 — Sentry wiring (env-gated)

**Files:** `SentryConfig.java` (or just `application.yml`).

Sentry's Spring Boot starter is config-driven. The `application.yml` from Task 2 sets `sentry.enabled: ${SENTRY_DSN:false}` so it's off when DSN absent. Add a quick `SentryTestController`:

```java
@RestController
@Profile("!prod")                       // only available in dev/test
public class SentryTestController {
  @GetMapping("/api/dev/sentry-test")
  public void throwIt() { throw new RuntimeException("Sentry test"); }
}
```

Manual smoke: with `SENTRY_DSN` set, hit `/api/dev/sentry-test`, verify event appears in Sentry dashboard within 30s. Document in `docs/release-checklist.md` (created in M6).

```bash
git add backend/src/main/resources/application.yml backend/src/main/java/com/lastminute/common
git commit -m "feat(sentry): env-gated server error reporting"
```

---

## Task 14 — Frontend scaffold (Vite + React + Tailwind + TanStack Query)

**Files:** `frontend/package.json`, `vite.config.ts`, `tailwind.config.ts`, `src/main.tsx`, `src/App.tsx`, `src/routes.tsx`, `src/api/client.ts`, `src/api/listings.ts`, `src/pages/home.tsx`, `src/pages/category.tsx`, `src/pages/listing.tsx`, `src/pages/signin.tsx`, `src/components/header.tsx`, `src/components/listing-card.tsx`, `src/components/relative-time.tsx`, `src/components/empty-state.tsx`, `src/lib/return-to.ts`, `src/lib/return-to.test.ts`.

- [ ] **Step 1: Init**

```bash
cd /mnt/c/Users/aqdas/Downloads/last-minute
pnpm create vite frontend --template react-ts
cd frontend
pnpm install
pnpm add react-router-dom @tanstack/react-query
pnpm add -D tailwindcss @tailwindcss/postcss @tailwindcss/vite vitest @testing-library/react @testing-library/jest-dom jsdom @types/node
```

- [ ] **Step 2: Configure Tailwind v4** (`@import "tailwindcss"` in `src/styles/globals.css`; add `@tailwindcss/vite` plugin in `vite.config.ts`).

- [ ] **Step 3: `src/api/client.ts`**

```ts
const BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
    ...init,
  })
  if (!res.ok) throw new ApiError(res.status, await res.text())
  return res.json()
}
export class ApiError extends Error { constructor(public status: number, public body: string) { super(`HTTP ${status}`) } }
```

- [ ] **Step 4: `src/api/listings.ts`**

```ts
import { api } from './client'
export type Listing = { id: string; title: string; city: string|null; originalPriceCents: number; discountedPriceCents: number; currency: string; startTime: string; endTime: string; images: string[] }
export const startingSoon = (city?: string) => api<Listing[]>(`/api/listings${city ? `?city=${encodeURIComponent(city)}` : ''}`)
export const listingById  = (id: string) => api<Listing>(`/api/listings/${id}`)
export const byCategory   = (slug: string) => api<Listing[]>(`/api/categories/${slug}/listings`)
```

- [ ] **Step 5: `src/lib/return-to.ts` (mirrors backend regex; **parity test verifies**)**

Same regex from `ReturnToValidator`. Add a tiny test that calls a backend `/api/dev/return-to-allowed?path=...` endpoint (dev profile only) and asserts the frontend regex agrees on a sample matrix. This is the only "safety net" for the duplicated regex.

- [ ] **Step 6: Pages**

`home.tsx`: hero (`Tonight's deals, before they're gone.`) + `<StartingSoonFeed/>` using `useQuery({ queryKey: ['starting-soon'], queryFn: () => startingSoon() })`. Empty state. Sign-in CTA when logged out.

`category.tsx`: read `:slug` param; `useQuery`; render `ListingCard` grid; empty state.

`listing.tsx`: read `:id` param; render title, description, category badge, provider name, prices, non-refundable disclosure, "Booking enabled in next milestone."

`signin.tsx`: magic-link form → `POST /api/auth/magic/request`. "Continue with Google" → `window.location = ${BASE}/oauth2/authorization/google?state=${returnTo}`.

- [ ] **Step 7: Router + App**

`routes.tsx` wires `/`, `/c/:slug`, `/l/:id`, `/signin` to the page components. `App.tsx` provides QueryClient + Router + global Header + main content area.

- [ ] **Step 8: Verify locally**

```bash
pnpm dev
# In another terminal: ensure backend is running
# Visit http://localhost:5173 → hero + (empty) feed
# /signin → magic-link form (no backend submit yet — verify in next task)
```

- [ ] **Step 9: Commit**

```bash
cd ..
git add frontend/
git commit -m "feat(frontend): Vite + React + Tailwind v4 + TanStack Query scaffold; consumer pages"
```

---

## Task 15 — Monorepo CI (GitHub Actions)

**Files:** `.github/workflows/ci.yml`.

```yaml
name: ci
on: [pull_request, push]

jobs:
  backend:
    runs-on: ubuntu-latest
    timeout-minutes: 10
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: maven }
      - run: cd backend && ./mvnw -B verify

  frontend:
    runs-on: ubuntu-latest
    timeout-minutes: 5
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v4
        with: { version: 9.12.0 }
      - uses: actions/setup-node@v4
        with: { node-version: '20.10.0', cache: pnpm, cache-dependency-path: frontend/pnpm-lock.yaml }
      - run: cd frontend && pnpm install --frozen-lockfile && pnpm tsc --noEmit && pnpm lint && pnpm test
```

```bash
git add .github/workflows/ci.yml
git commit -m "ci: backend (Maven verify) + frontend (tsc/lint/test) on every PR"
```

---

## Task 16 — Seed script + end-to-end manual smoke

**Files:** `backend/src/main/java/com/lastminute/admin/Seed.java` (`@Component` running only with profile `dev-seed`).

Seeds: one admin user, one category ("fitness"), one provider, one listing starting in 2 hours.

- [ ] **Walk acceptance criteria locally**:
  1. `pnpm dev` (frontend) and `./mvnw spring-boot:run` (backend) both running.
  2. Visit `http://localhost:5173` → hero + the seeded listing.
  3. Visit `/c/fitness` → same listing.
  4. Click listing → detail page with provider/category/price.
  5. Sign in as admin (magic link); visit `/admin/listings`; create another listing; refresh `/` after 15s → it appears.
  6. `./mvnw test` → all backend tests pass.
  7. `pnpm test` (in frontend/) → all frontend tests pass.

- [ ] Commit seed.

---

## Acceptance criteria

| # | Criterion | Covered by |
|---|---|---|
| 1 | Sign in via magic link, see populated home feed | Tasks 9, 14, 16 |
| 2 | Browse category + listing detail with tz-aware relative times | Tasks 10, 14 |
| 3 | Admin seeds categories + provider + listing; appears in feed | Tasks 11, 12, 16 |
| 4 | `PricingService` boundary tests pass full §3.2 set | Task 5 |
| 5 | ArchUnit rule catches `Instant.now()` outside the clock bean | Task 4 |
| 6 | CI green: backend verify + frontend test ≤ 10 min | Task 15 |

## Self-review

- File paths consistent under `backend/src/main/java/com/lastminute/...` and `frontend/src/...`.
- Type signatures referenced across tasks (`Clock`, `Listing`, `CurrentUser`, `ListingQueryService`) are defined in earlier tasks.
- Every TDD task writes a test first.
- No placeholders; where Spring conventions are obvious, the plan links to them rather than copy-pastes hundreds of lines (executor is a Java developer).

## Execution handoff

Plan saved to `docs/superpowers/plans/2026-05-27-m1-spring-boot-foundation.md`.

**Recommended execution:** subagent-driven, batched checkpoints (Batch 1 = Tasks 1–4; Batch 2 = 5–9; Batch 3 = 10–13; Batch 4 = 14–16).

When you return, install Java 21 + Maven first (see Prerequisites), then we can kick off Batch 1.
