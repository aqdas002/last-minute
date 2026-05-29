package com.lastminute.support;

import com.lastminute.categories.Category;
import com.lastminute.categories.CategoryRepository;
import com.lastminute.listings.Listing;
import com.lastminute.listings.ListingRepository;
import com.lastminute.listings.ListingStatus;
import com.lastminute.providers.Provider;
import com.lastminute.providers.ProviderRepository;
import com.lastminute.providers.ProviderStatus;
import com.lastminute.users.User;
import com.lastminute.users.UserRepository;
import com.lastminute.users.UserRole;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Test data factories. Add fields here as later milestones extend entities. */
@Component
public class Factories {

  private static final AtomicInteger COUNTER = new AtomicInteger();

  @Autowired private UserRepository users;
  @Autowired private ProviderRepository providers;
  @Autowired private CategoryRepository categories;
  @Autowired private ListingRepository listings;

  public User user() {
    return user("u" + COUNTER.incrementAndGet() + "@test.local", UserRole.consumer);
  }

  public User user(String email, UserRole role) {
    User u = new User();
    u.setEmail(email);
    u.setRole(role);
    return users.save(u);
  }

  public Provider provider() {
    User u = user("p" + COUNTER.incrementAndGet() + "@test.local", UserRole.provider);
    Provider p = new Provider();
    p.setId(u.getId());
    p.setBusinessName("Biz " + u.getId().toString().substring(0, 8));
    p.setCurrency("USD");
    p.setTimezone("America/New_York");
    p.setStatus(ProviderStatus.active);
    p.setStripeChargesEnabled(true);
    p.setStripePayoutsEnabled(true);
    p.setCity("New York");
    p.setCountry("US");
    return providers.save(p);
  }

  public Category category() {
    return category("cat-" + COUNTER.incrementAndGet());
  }

  public Category category(String slug) {
    Category c = new Category();
    c.setSlug(slug);
    c.setName("Category " + slug);
    return categories.save(c);
  }

  /** Build a listing with full overrides; sensible defaults for any field left null. */
  public Listing listing(ListingOptions opts) {
    Provider p = opts.provider() != null ? opts.provider() : provider();
    Category c = opts.category() != null ? opts.category() : category();
    Instant now = opts.now() != null ? opts.now() : Instant.now();
    Listing l = new Listing();
    l.setProvider(p);
    l.setCategory(c);
    l.setTitle(opts.title() != null ? opts.title() : "Listing " + UUID.randomUUID());
    l.setOriginalPriceCents(opts.originalPriceCents() != null ? opts.originalPriceCents() : 12000);
    l.setDiscountedPriceCents(
        opts.discountedPriceCents() != null ? opts.discountedPriceCents() : 8000);
    l.setCurrency("USD");
    l.setCapacity(opts.capacity() != null ? opts.capacity() : 1);
    l.setStartTime(opts.startTime() != null ? opts.startTime() : now.plusSeconds(3 * 3600));
    l.setEndTime(opts.endTime() != null ? opts.endTime() : now.plusSeconds(4 * 3600));
    l.setListingExpiresAt(
        opts.expiresAt() != null ? opts.expiresAt() : now.plusSeconds(2 * 3600 + 50 * 60));
    l.setTimezone("America/New_York");
    l.setStatus(opts.status() != null ? opts.status() : ListingStatus.active);
    l.setCity(opts.city() != null ? opts.city() : "New York");
    l.setLat(40.7128);
    l.setLon(-74.006);
    return listings.save(l);
  }

  public record ListingOptions(
      Provider provider,
      Category category,
      String title,
      Instant now,
      Instant startTime,
      Instant endTime,
      Instant expiresAt,
      Integer originalPriceCents,
      Integer discountedPriceCents,
      Integer capacity,
      ListingStatus status,
      String city) {

    public static Builder b() {
      return new Builder();
    }

    public static final class Builder {
      private Provider provider;
      private Category category;
      private String title;
      private Instant now;
      private Instant startTime;
      private Instant endTime;
      private Instant expiresAt;
      private Integer originalPriceCents;
      private Integer discountedPriceCents;
      private Integer capacity;
      private ListingStatus status;
      private String city;

      public Builder provider(Provider p) { this.provider = p; return this; }
      public Builder category(Category c) { this.category = c; return this; }
      public Builder title(String t) { this.title = t; return this; }
      public Builder now(Instant n) { this.now = n; return this; }
      public Builder startTime(Instant s) { this.startTime = s; return this; }
      public Builder endTime(Instant e) { this.endTime = e; return this; }
      public Builder expiresAt(Instant e) { this.expiresAt = e; return this; }
      public Builder priceCents(int original, int discounted) {
        this.originalPriceCents = original; this.discountedPriceCents = discounted; return this;
      }
      public Builder capacity(int c) { this.capacity = c; return this; }
      public Builder status(ListingStatus s) { this.status = s; return this; }
      public Builder city(String c) { this.city = c; return this; }

      public ListingOptions build() {
        return new ListingOptions(
            provider, category, title, now, startTime, endTime, expiresAt,
            originalPriceCents, discountedPriceCents, capacity, status, city);
      }
    }
  }
}
