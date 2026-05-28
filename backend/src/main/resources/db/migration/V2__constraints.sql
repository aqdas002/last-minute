ALTER TABLE listings
  ADD CONSTRAINT listings_capacity_ck            CHECK (capacity >= 1),
  ADD CONSTRAINT listings_prices_ck              CHECK (discounted_price_cents > 0 AND discounted_price_cents < original_price_cents),
  ADD CONSTRAINT listings_end_after_start_ck     CHECK (end_time > start_time),
  ADD CONSTRAINT listings_expires_before_end_ck  CHECK (listing_expires_at <= end_time);
