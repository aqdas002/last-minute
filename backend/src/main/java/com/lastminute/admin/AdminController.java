package com.lastminute.admin;

import com.lastminute.categories.Category;
import com.lastminute.categories.CategoryRepository;
import com.lastminute.listings.Listing;
import com.lastminute.listings.ListingDto;
import com.lastminute.listings.ListingRepository;
import com.lastminute.listings.ListingStatus;
import com.lastminute.providers.Provider;
import com.lastminute.providers.ProviderRepository;
import com.lastminute.providers.ProviderStatus;
import com.lastminute.users.User;
import com.lastminute.users.UserRepository;
import com.lastminute.users.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal seed endpoints for dogfooding M1 without provider self-onboarding (M2) or booking
 * (M3). Gated by {@code ROLE_ADMIN} in {@link com.lastminute.auth.SecurityConfig}.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

  private final UserRepository users;
  private final ProviderRepository providers;
  private final CategoryRepository categories;
  private final ListingRepository listings;
  private final Clock clock;

  public AdminController(
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

  // ───── Categories ─────────────────────────────────────────────

  public record CreateCategoryRequest(@NotBlank String slug, @NotBlank String name) {}

  @PostMapping("/categories")
  @CacheEvict(cacheNames = {"listings-by-category", "starting-soon"}, allEntries = true)
  public ResponseEntity<UUID> createCategory(@Valid @RequestBody CreateCategoryRequest body) {
    Category c = new Category();
    c.setSlug(body.slug());
    c.setName(body.name());
    return ResponseEntity.ok(categories.save(c).getId());
  }

  // ───── Providers (admin-mediated; Stripe Connect lands in M2) ──

  public record CreateProviderRequest(
      @NotBlank @Email String email,
      @NotBlank String businessName,
      @Size(min = 3, max = 3) String currency,
      @NotBlank String timezone) {}

  @PostMapping("/providers")
  public ResponseEntity<UUID> createProvider(@Valid @RequestBody CreateProviderRequest body) {
    User u = new User();
    u.setEmail(body.email());
    u.setRole(UserRole.provider);
    u = users.save(u);

    Provider p = new Provider();
    p.setId(u.getId());
    p.setBusinessName(body.businessName());
    p.setCurrency(body.currency() == null ? "USD" : body.currency());
    p.setTimezone(body.timezone());
    p.setStatus(ProviderStatus.active);
    p.setStripeChargesEnabled(true);
    p.setStripePayoutsEnabled(true);
    p.setCity("New York");
    p.setCountry("US");
    providers.save(p);
    return ResponseEntity.ok(u.getId());
  }

  // ───── Listings ────────────────────────────────────────────────

  public record CreateListingRequest(
      @NotBlank String providerId,
      @NotBlank String categoryId,
      @NotBlank String title,
      @Positive int originalPriceCents,
      @Min(50) int discountedPriceCents,
      @Positive double startHoursFromNow) {}

  @PostMapping("/listings")
  @CacheEvict(cacheNames = {"listings-by-category", "starting-soon"}, allEntries = true)
  @Transactional
  public ResponseEntity<ListingDto> createListing(@Valid @RequestBody CreateListingRequest body) {
    if (body.discountedPriceCents() >= body.originalPriceCents()) {
      return ResponseEntity.badRequest().build();
    }
    Provider p =
        providers
            .findById(UUID.fromString(body.providerId()))
            .orElseThrow(() -> new IllegalArgumentException("provider not found"));
    Category c =
        categories
            .findById(UUID.fromString(body.categoryId()))
            .orElseThrow(() -> new IllegalArgumentException("category not found"));

    Instant now = Instant.now(clock);
    long startMinutes = (long) (body.startHoursFromNow() * 60);
    Instant startTime = now.plus(startMinutes, ChronoUnit.MINUTES);
    Instant endTime = startTime.plus(60, ChronoUnit.MINUTES);
    Instant expiresAt = startTime.minus(10, ChronoUnit.MINUTES);

    Listing l = new Listing();
    l.setProvider(p);
    l.setCategory(c);
    l.setTitle(body.title());
    l.setOriginalPriceCents(body.originalPriceCents());
    l.setDiscountedPriceCents(body.discountedPriceCents());
    l.setCurrency("USD");
    l.setCapacity(1);
    l.setStartTime(startTime);
    l.setEndTime(endTime);
    l.setListingExpiresAt(expiresAt);
    l.setTimezone("America/New_York");
    l.setStatus(ListingStatus.active);
    l.setCity("New York");
    l.setLat(40.7128);
    l.setLon(-74.006);
    l.setImages(List.of());
    listings.save(l);
    return ResponseEntity.ok(ListingDto.from(l));
  }
}
