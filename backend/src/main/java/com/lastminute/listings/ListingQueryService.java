package com.lastminute.listings;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Single entry point for consumer-facing listing reads. Every method runs the spec §2 belt-and-
 * braces filter {@code listing_expires_at > clock.now()} via the repository — no other code should
 * read listings directly without that filter.
 */
@Service
public class ListingQueryService {

  private final ListingRepository repo;
  private final Clock clock;

  public ListingQueryService(ListingRepository repo, Clock clock) {
    this.repo = repo;
    this.clock = clock;
  }

  public List<Listing> startingSoon(String city) {
    return repo.findStartingSoon(Instant.now(clock), city, ListingStatus.active);
  }

  public List<Listing> byCategorySlug(String slug) {
    return repo.findActiveByCategorySlug(Instant.now(clock), slug, ListingStatus.active);
  }

  public Optional<Listing> byId(UUID id) {
    return repo.findActiveById(Instant.now(clock), id, ListingStatus.active);
  }
}
