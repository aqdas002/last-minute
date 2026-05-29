package com.lastminute.listings;

/** Maps to Postgres enum {@code listing_status}. */
public enum ListingStatus {
  draft,
  active,
  sold_out,
  expired,
  cancelled,
  suspended
}
