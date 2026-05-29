package com.lastminute.admin;

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
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Runs only with the {@code dev-seed} profile active. Seeds an admin user, one category, one
 * provider, and one listing starting in 2 hours so the consumer surfaces have something to render.
 * Idempotent — re-running does nothing if the admin already exists.
 */
@Component
@Profile("dev-seed")
public class DevSeed implements CommandLineRunner {

  private static final Logger LOG = LoggerFactory.getLogger(DevSeed.class);
  private static final String ADMIN_EMAIL_PROP = "SEED_ADMIN_EMAIL";

  private final UserRepository users;
  private final ProviderRepository providers;
  private final CategoryRepository categories;
  private final ListingRepository listings;
  private final Clock clock;

  public DevSeed(
      UserRepository users,
      ProviderRepository providers,
      CategoryRepository categories,
      ListingRepository listings,
      Clock clock) {
    this.users = users;
    this.providers = providers;
    this.categories = categories;
    this.listings = listings;
    this.clock = clock;
  }

  @Override
  public void run(String... args) {
    String adminEmail =
        System.getenv().getOrDefault(ADMIN_EMAIL_PROP, "admin@local");
    if (users.findByEmail(adminEmail).isPresent()) {
      LOG.info("Dev seed: admin {} already exists, skipping", adminEmail);
      return;
    }

    User admin = new User();
    admin.setEmail(adminEmail);
    admin.setRole(UserRole.admin);
    users.save(admin);

    Category fitness = categories.findBySlug("fitness").orElseGet(() -> {
      Category c = new Category();
      c.setSlug("fitness");
      c.setName("Fitness");
      return categories.save(c);
    });

    User provUser = new User();
    provUser.setEmail("studio@example.com");
    provUser.setRole(UserRole.provider);
    provUser = users.save(provUser);

    Provider prov = new Provider();
    prov.setId(provUser.getId());
    prov.setBusinessName("Sunset Yoga Studio");
    prov.setCurrency("USD");
    prov.setTimezone("America/New_York");
    prov.setStatus(ProviderStatus.active);
    prov.setStripeChargesEnabled(true);
    prov.setStripePayoutsEnabled(true);
    prov.setCity("New York");
    prov.setCountry("US");
    providers.save(prov);

    Instant now = Instant.now(clock);
    Listing l = new Listing();
    l.setProvider(prov);
    l.setCategory(fitness);
    l.setTitle("7pm vinyasa flow (45 min)");
    l.setDescription("Warm vinyasa with live music. Mats provided.");
    l.setOriginalPriceCents(2500);
    l.setDiscountedPriceCents(1500);
    l.setCurrency("USD");
    l.setCapacity(8);
    l.setStartTime(now.plus(2, ChronoUnit.HOURS));
    l.setEndTime(now.plus(2, ChronoUnit.HOURS).plus(45, ChronoUnit.MINUTES));
    l.setListingExpiresAt(now.plus(110, ChronoUnit.MINUTES));
    l.setTimezone("America/New_York");
    l.setStatus(ListingStatus.active);
    l.setCity("New York");
    l.setLat(40.7128);
    l.setLon(-74.006);
    l.setImages(List.of());
    listings.save(l);

    LOG.info("Dev seed complete. Admin={}, listing visible at /api/listings", adminEmail);
  }
}
