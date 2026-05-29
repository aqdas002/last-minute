package com.lastminute.providers;

import com.lastminute.categories.Category;
import com.lastminute.categories.CategoryRepository;
import com.lastminute.listings.Listing;
import com.lastminute.listings.ListingRepository;
import com.lastminute.listings.ListingStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Provider self-serve listing operations. Spec §6.2 material vs non-material edit split: the
 * structural rejection path is wired in M2 (this commit); the actual active-bookings predicate
 * lights up in M3 when the {@code bookings} table arrives.
 */
@Service
public class ProviderListingService {

  private final ListingRepository listings;
  private final ProviderRepository providers;
  private final CategoryRepository categories;
  private final Clock clock;

  public ProviderListingService(
      ListingRepository listings,
      ProviderRepository providers,
      CategoryRepository categories,
      Clock clock) {
    this.listings = listings;
    this.providers = providers;
    this.categories = categories;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<Listing> mine(UUID providerId) {
    List<Listing> result = listings.findAllByProviderIdOrderByStartTimeAsc(providerId);
    // Force association load inside the tx so DTO.from() doesn't trip LazyInitializationException.
    result.forEach(
        l -> {
          l.getCategory().getSlug();
          l.getProvider().getBusinessName();
        });
    return result;
  }

  @Transactional
  public Listing create(UUID providerId, CreateRequest req) {
    Provider provider =
        providers
            .findById(providerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no_provider"));

    validateRequest(req);

    Category category =
        categories
            .findById(req.categoryId())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown_category"));

    Listing l = new Listing();
    l.setProvider(provider);
    l.setCategory(category);
    l.setTitle(req.title());
    l.setDescription(req.description());
    l.setImages(new ArrayList<>());
    l.setOriginalPriceCents(req.originalPriceCents());
    l.setDiscountedPriceCents(req.discountedPriceCents());
    l.setCurrency(provider.getCurrency());
    l.setCapacity(req.capacity());
    l.setStartTime(req.startTime());
    l.setEndTime(req.endTime());
    l.setListingExpiresAt(req.listingExpiresAt());
    l.setTimezone(provider.getTimezone());
    // Always draft on creation per spec §5 Flow 2 — provider explicitly publishes.
    l.setStatus(ListingStatus.draft);
    l.setCity(provider.getCity());
    l.setLat(provider.getDefaultLat());
    l.setLon(provider.getDefaultLon());
    return listings.save(l);
  }

  @Transactional
  public Listing edit(UUID providerId, UUID listingId, EditRequest req) {
    Listing l = findOwn(providerId, listingId);
    // Eager-touch for DTO serialization outside the tx.
    l.getCategory().getSlug();
    l.getProvider().getBusinessName();

    if (req.requestsMaterialChange() && hasActiveBookings(listingId)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "HAS_ACTIVE_BOOKINGS");
    }

    // Non-material fields — always editable.
    if (req.title() != null) l.setTitle(req.title());
    if (req.description() != null) l.setDescription(req.description());
    if (req.images() != null) l.setImages(req.images());

    // Material fields — only reach here if no active bookings.
    if (req.originalPriceCents() != null) l.setOriginalPriceCents(req.originalPriceCents());
    if (req.discountedPriceCents() != null) l.setDiscountedPriceCents(req.discountedPriceCents());
    if (req.capacity() != null) l.setCapacity(req.capacity());
    if (req.startTime() != null) l.setStartTime(req.startTime());
    if (req.endTime() != null) l.setEndTime(req.endTime());
    if (req.listingExpiresAt() != null) l.setListingExpiresAt(req.listingExpiresAt());

    return listings.save(l);
  }

  @Transactional
  public Listing publish(UUID providerId, UUID listingId) {
    Listing l = findOwn(providerId, listingId);
    Provider p = l.getProvider();
    p.getBusinessName(); // force-load
    l.getCategory().getSlug();
    if (!p.isStripeChargesEnabled()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "stripe_charges_not_enabled");
    }
    if (l.getStatus() != ListingStatus.draft) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "already_published");
    }
    l.setStatus(ListingStatus.active);
    return listings.save(l);
  }

  private Listing findOwn(UUID providerId, UUID listingId) {
    Listing l =
        listings
            .findById(listingId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
    if (!l.getProvider().getId().equals(providerId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found");
    }
    return l;
  }

  /**
   * M2: bookings table doesn't exist yet, so always 0. M3 replaces this with a real query against
   * the bookings table filtered by {@code status IN ('pending','confirmed')}.
   */
  private boolean hasActiveBookings(UUID listingId) {
    return false;
  }

  private void validateRequest(CreateRequest req) {
    if (req.discountedPriceCents() < 50) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "discounted_below_50_cents");
    }
    if (req.discountedPriceCents() >= req.originalPriceCents()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "discounted_must_be_below_original");
    }
    if (req.capacity() < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "capacity_below_1");
    }
    Instant now = Instant.now(clock);
    if (!req.startTime().isAfter(now)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "start_time_in_past");
    }
    if (!req.endTime().isAfter(req.startTime())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "end_before_start");
    }
    if (req.listingExpiresAt().isAfter(req.endTime())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expires_after_end");
    }
  }

  public record CreateRequest(
      UUID categoryId,
      String title,
      String description,
      int originalPriceCents,
      int discountedPriceCents,
      int capacity,
      Instant startTime,
      Instant endTime,
      Instant listingExpiresAt) {}

  public record EditRequest(
      String title,
      String description,
      List<String> images,
      Integer originalPriceCents,
      Integer discountedPriceCents,
      Integer capacity,
      Instant startTime,
      Instant endTime,
      Instant listingExpiresAt) {

    public boolean requestsMaterialChange() {
      return originalPriceCents != null
          || discountedPriceCents != null
          || capacity != null
          || startTime != null
          || endTime != null
          || listingExpiresAt != null;
    }
  }
}
