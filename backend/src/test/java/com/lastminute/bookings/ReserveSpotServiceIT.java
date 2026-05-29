package com.lastminute.bookings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lastminute.categories.Category;
import com.lastminute.categories.CategoryRepository;
import com.lastminute.listings.Listing;
import com.lastminute.listings.ListingRepository;
import com.lastminute.listings.ListingStatus;
import com.lastminute.providers.Provider;
import com.lastminute.providers.ProviderRepository;
import com.lastminute.providers.ProviderStatus;
import com.lastminute.support.IntegrationTestBase;
import com.lastminute.users.User;
import com.lastminute.users.UserRepository;
import com.lastminute.users.UserRole;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

class ReserveSpotServiceIT extends IntegrationTestBase {

  @Autowired ReserveSpotService svc;
  @Autowired BookingRepository bookings;
  @Autowired ListingRepository listings;
  @Autowired ProviderRepository providers;
  @Autowired CategoryRepository categories;
  @Autowired UserRepository users;

  private Listing seedListing(int capacity) {
    User pUser = new User();
    pUser.setEmail("p" + UUID.randomUUID() + "@x");
    pUser.setRole(UserRole.provider);
    pUser = users.save(pUser);

    Provider p = new Provider();
    p.setId(pUser.getId());
    p.setBusinessName("Studio");
    p.setCurrency("USD");
    p.setTimezone("UTC");
    p.setStatus(ProviderStatus.active);
    p.setStripeChargesEnabled(true);
    providers.save(p);

    Category c = new Category();
    c.setSlug("c" + UUID.randomUUID());
    c.setName("C");
    c = categories.save(c);

    Listing l = new Listing();
    l.setProvider(p);
    l.setCategory(c);
    l.setTitle("test");
    l.setOriginalPriceCents(10000);
    l.setDiscountedPriceCents(8000);
    l.setCurrency("USD");
    l.setCapacity(capacity);
    Instant start = Instant.now().plusSeconds(7200);
    l.setStartTime(start);
    l.setEndTime(start.plusSeconds(3600));
    l.setListingExpiresAt(start.minusSeconds(600));
    l.setTimezone("UTC");
    l.setStatus(ListingStatus.active);
    l.setImages(new ArrayList<>());
    return listings.save(l);
  }

  private UUID seedConsumer() {
    User u = new User();
    u.setEmail("c" + UUID.randomUUID() + "@x");
    u.setRole(UserRole.consumer);
    return users.save(u).getId();
  }

  @Test
  void happy_path_creates_pending_booking_with_correct_fees() {
    Listing l = seedListing(1);
    UUID c = seedConsumer();

    var result = svc.reserveSpot(l.getId(), c);

    assertThat(result.newlyCreated()).isTrue();
    Booking b = result.booking();
    assertThat(b.getStatus()).isEqualTo(BookingStatus.pending);
    assertThat(b.getAmountPaidCents()).isEqualTo(8000);
    assertThat(b.getPlatformFeeCents()).isEqualTo(1200); // floor(8000 * 15 / 100)
    assertThat(b.getProviderPayoutCents()).isEqualTo(6800);
    assertThat(b.getRedemptionCode()).hasSize(8);
    assertThat(b.getPendingExpiresAt()).isAfter(Instant.now().plusSeconds(60 * 30));
  }

  @Test
  void double_tap_returns_existing_pending_booking() {
    Listing l = seedListing(1);
    UUID c = seedConsumer();

    var first = svc.reserveSpot(l.getId(), c);
    var second = svc.reserveSpot(l.getId(), c);

    assertThat(first.newlyCreated()).isTrue();
    assertThat(second.newlyCreated()).isFalse();
    assertThat(second.booking().getId()).isEqualTo(first.booking().getId());
    assertThat(bookings.count()).isEqualTo(1);
  }

  @Test
  void second_consumer_on_capacity_1_listing_sold_out() {
    Listing l = seedListing(1);
    UUID first = seedConsumer();
    UUID second = seedConsumer();

    svc.reserveSpot(l.getId(), first);

    assertThatThrownBy(() -> svc.reserveSpot(l.getId(), second))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("SOLD_OUT");
  }

  @Test
  void expired_listing_throws_listing_expired() {
    Listing l = seedListing(1);
    l.setListingExpiresAt(Instant.now().minusSeconds(60));
    listings.save(l);
    UUID c = seedConsumer();

    assertThatThrownBy(() -> svc.reserveSpot(l.getId(), c))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("LISTING_EXPIRED");
  }

  /**
   * Spec §5 Flow 1 concurrency invariant: N parallel reserves on a capacity=1 listing produce
   * exactly 1 confirmed slot. SELECT FOR UPDATE on the listing row serializes them; losers see
   * SOLD_OUT.
   */
  @Test
  void concurrent_reserves_on_capacity_1_yield_exactly_one_winner() throws Exception {
    Listing l = seedListing(1);
    final int N = 10;
    List<UUID> consumers = new ArrayList<>();
    for (int i = 0; i < N; i++) consumers.add(seedConsumer());

    ExecutorService pool = Executors.newFixedThreadPool(N);
    try {
      List<Future<String>> futures = new ArrayList<>();
      for (UUID cid : consumers) {
        Callable<String> task =
            () -> {
              try {
                svc.reserveSpot(l.getId(), cid);
                return "WIN";
              } catch (ResponseStatusException e) {
                return "LOSE:" + e.getReason();
              } catch (Exception e) {
                return "ERR:" + e.getClass().getSimpleName();
              }
            };
        futures.add(pool.submit(task));
      }

      AtomicInteger wins = new AtomicInteger();
      AtomicInteger soldOut = new AtomicInteger();
      AtomicInteger other = new AtomicInteger();
      for (Future<String> f : futures) {
        String s = f.get(30, TimeUnit.SECONDS);
        if ("WIN".equals(s)) wins.incrementAndGet();
        else if (s.startsWith("LOSE:SOLD_OUT")) soldOut.incrementAndGet();
        else other.incrementAndGet();
      }

      assertThat(wins.get()).as("exactly one winner").isEqualTo(1);
      assertThat(soldOut.get()).as("the other N-1 see SOLD_OUT").isEqualTo(N - 1);
      assertThat(other.get()).as("no unexpected errors").isEqualTo(0);
      assertThat(bookings.count()).isEqualTo(1);
    } finally {
      pool.shutdown();
    }
  }
}
