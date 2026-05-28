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

-- Magic-link verification tokens (mirrors Auth.js / NextAuth's verification_tokens shape
-- since we're implementing magic links manually in MagicLinkService)
CREATE TABLE verification_tokens (
  identifier  TEXT NOT NULL,
  token       TEXT NOT NULL UNIQUE,
  expires     TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (identifier, token)
);
