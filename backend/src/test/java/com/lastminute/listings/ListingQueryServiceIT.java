package com.lastminute.listings;

import static org.assertj.core.api.Assertions.assertThat;

import com.lastminute.categories.Category;
import com.lastminute.support.Factories;
import com.lastminute.support.Factories.ListingOptions;
import com.lastminute.support.IntegrationTestBase;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import(ListingQueryServiceIT.FixedClock.class)
class ListingQueryServiceIT extends IntegrationTestBase {

  static final Instant T0 = Instant.parse("2026-06-01T12:00:00Z");

  @TestConfiguration
  static class FixedClock {
    @Bean
    @Primary
    Clock testClock() {
      return Clock.fixed(T0, ZoneOffset.UTC);
    }
  }

  @Autowired private Factories f;
  @Autowired private ListingQueryService q;

  @Test
  void excludes_listings_past_listing_expires_at() {
    Category yoga = f.category("yoga");
    f.listing(
        ListingOptions.b()
            .category(yoga)
            .title("Past expiry")
            .now(T0)
            .expiresAt(T0.minusSeconds(60))
            .startTime(T0.plusSeconds(3600))
            .endTime(T0.plusSeconds(5400))
            .build());
    f.listing(
        ListingOptions.b()
            .category(yoga)
            .title("Still bookable")
            .now(T0)
            .expiresAt(T0.plusSeconds(3600))
            .startTime(T0.plusSeconds(2 * 3600))
            .endTime(T0.plusSeconds(3 * 3600))
            .build());

    assertThat(q.byCategorySlug("yoga"))
        .extracting(Listing::getTitle)
        .containsExactly("Still bookable");
  }

  @Test
  void starting_soon_also_excludes_expired() {
    f.listing(
        ListingOptions.b()
            .now(T0)
            .expiresAt(T0.minusSeconds(1))
            .startTime(T0.plusSeconds(3600))
            .endTime(T0.plusSeconds(5400))
            .build());
    assertThat(q.startingSoon("New York")).isEmpty();
  }

  @Test
  void excludes_non_active_status() {
    Category sf = f.category("status-filter");
    Instant expires = T0.plusSeconds(3600);
    Instant start = T0.plusSeconds(2 * 3600);
    Instant end = start.plusSeconds(3600);
    for (ListingStatus s :
        new ListingStatus[] {
          ListingStatus.draft,
          ListingStatus.suspended,
          ListingStatus.cancelled,
          ListingStatus.expired,
          ListingStatus.sold_out
        }) {
      f.listing(
          ListingOptions.b()
              .category(sf)
              .title(s.name())
              .now(T0)
              .expiresAt(expires)
              .startTime(start)
              .endTime(end)
              .status(s)
              .build());
    }
    assertThat(q.byCategorySlug("status-filter")).isEmpty();
  }

  @Test
  void city_filter_actually_filters() {
    f.listing(
        ListingOptions.b()
            .title("NYC")
            .city("New York")
            .now(T0)
            .expiresAt(T0.plusSeconds(3600))
            .startTime(T0.plusSeconds(2 * 3600))
            .endTime(T0.plusSeconds(3 * 3600))
            .build());
    f.listing(
        ListingOptions.b()
            .title("LA")
            .city("Los Angeles")
            .now(T0)
            .expiresAt(T0.plusSeconds(3600))
            .startTime(T0.plusSeconds(2 * 3600))
            .endTime(T0.plusSeconds(3 * 3600))
            .build());
    assertThat(q.startingSoon("New York"))
        .extracting(Listing::getTitle)
        .containsExactly("NYC");
  }

  @Test
  void equality_on_listing_expires_at_excluded_strict_gt() {
    Category b = f.category("boundary");
    f.listing(
        ListingOptions.b()
            .category(b)
            .title("Exact-boundary")
            .now(T0)
            .expiresAt(T0)
            .startTime(T0.plusSeconds(60))
            .endTime(T0.plusSeconds(120))
            .build());
    assertThat(q.byCategorySlug("boundary")).isEmpty();
  }

  @Test
  void by_id_returns_empty_when_expired() {
    Listing l =
        f.listing(
            ListingOptions.b()
                .title("Will-expire")
                .now(T0)
                .expiresAt(T0.minusSeconds(1))
                .startTime(T0.plusSeconds(60))
                .endTime(T0.plusSeconds(120))
                .build());
    assertThat(q.byId(l.getId())).isEmpty();
  }

  @Test
  void preserves_emoji_in_title_round_trip() {
    Category cat = f.category("emoji");
    f.listing(
        ListingOptions.b()
            .category(cat)
            .title("Yoga 🧘 sunset 🌅")
            .now(T0)
            .expiresAt(T0.plusSeconds(3600))
            .startTime(T0.plusSeconds(2 * 3600))
            .endTime(T0.plusSeconds(3 * 3600))
            .build());
    assertThat(q.byCategorySlug("emoji"))
        .first()
        .extracting(Listing::getTitle)
        .isEqualTo("Yoga 🧘 sunset 🌅");
  }
}
