package com.lastminute.bookings;

import com.lastminute.listings.Listing;
import com.lastminute.listings.ListingStatus;
import com.lastminute.pricing.PricingService;
import com.lastminute.users.User;
import com.lastminute.users.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Spec §5 Flow 1 steps 3–4: the core booking transaction. Stripe Checkout creation happens
 * OUTSIDE this transaction (in {@code BookingController}) so the row lock isn't held across a
 * network call.
 *
 * <p>Lock granularity is the listing row; only writers booking the SAME listing serialize. Stripe
 * Checkout's documented minimum {@code expires_at} is 30 minutes, so we set {@code
 * pending_expires_at = now + 35 min} to give the session time to expire on its own before our
 * sweeper takes it.
 */
@Service
public class ReserveSpotService {

  static final Duration PENDING_TTL = Duration.ofMinutes(35);
  static final List<BookingStatus> ACTIVE = List.of(BookingStatus.pending, BookingStatus.confirmed);

  private final BookingRepository bookings;
  private final UserRepository users;
  private final RedemptionCodeGenerator codes;
  private final PricingService pricing;
  private final Clock clock;

  public ReserveSpotService(
      BookingRepository bookings,
      UserRepository users,
      RedemptionCodeGenerator codes,
      PricingService pricing,
      Clock clock) {
    this.bookings = bookings;
    this.users = users;
    this.codes = codes;
    this.pricing = pricing;
    this.clock = clock;
  }

  @Transactional
  public ReserveResult reserveSpot(UUID listingId, UUID consumerId) {
    // Pessimistic write lock on the listing row — serializes concurrent bookers on this listing.
    Listing listing =
        bookings
            .lockListingForUpdate(listingId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND"));

    Instant now = Instant.now(clock);
    if (listing.getStatus() != ListingStatus.active) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "LISTING_NOT_ACTIVE");
    }
    if (!listing.getListingExpiresAt().isAfter(now)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "LISTING_EXPIRED");
    }

    // Fast-path: same consumer tapping Book a second time → return the existing pending booking.
    var existing = bookings.findActiveForConsumer(listingId, consumerId, ACTIVE);
    if (existing.isPresent()) {
      return new ReserveResult(existing.get(), false);
    }

    long activeCount = bookings.countByListingAndStatuses(listingId, ACTIVE);
    if (activeCount >= listing.getCapacity()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "SOLD_OUT");
    }

    User consumer =
        users
            .findById(consumerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no_user"));

    Booking b = new Booking();
    b.setListing(listing);
    b.setConsumer(consumer);
    b.setProvider(listing.getProvider());
    b.setStatus(BookingStatus.pending);
    int amountCents = listing.getDiscountedPriceCents();
    b.setAmountPaidCents(amountCents);
    b.setPlatformFeeCents((int) pricing.platformFeeCents(amountCents));
    b.setProviderPayoutCents((int) pricing.providerPayoutCents(amountCents));
    b.setCurrency(listing.getCurrency());
    b.setPendingExpiresAt(now.plus(PENDING_TTL));
    b.setRedemptionCode(saveWithUniqueCode(b));

    return new ReserveResult(b, true);
  }

  /**
   * Retry-on-collision insert for the redemption code (the partial-unique-index {@code
   * (provider_id, redemption_code) WHERE status IN (pending, confirmed)} can collide at random,
   * however unlikely with a 31^8 ≈ 8.5e11 namespace).
   */
  private String saveWithUniqueCode(Booking b) {
    final int MAX_ATTEMPTS = 5;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      String code = codes.generate();
      b.setRedemptionCode(code);
      try {
        bookings.saveAndFlush(b);
        return code;
      } catch (DataIntegrityViolationException dup) {
        if (attempt == MAX_ATTEMPTS) throw dup;
        // Detach so the next attempt can flush again with a new code.
      }
    }
    throw new IllegalStateException("unreachable");
  }

  public record ReserveResult(Booking booking, boolean newlyCreated) {}
}
