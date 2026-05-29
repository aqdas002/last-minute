package com.lastminute.listings;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

  /**
   * Consumer-facing "starting soon" feed. Strict {@code listing_expires_at > now} (belt-and-braces
   * per spec §2 caching). Optional city filter. Status is passed as a parameter so the JPQL
   * enum literal doesn't generate a Java-class-name cast Postgres can't resolve.
   */
  @Query(
      """
      SELECT l FROM Listing l
      WHERE l.status = :status
        AND l.listingExpiresAt > :now
        AND (:city IS NULL OR l.city = :city)
      ORDER BY l.startTime ASC
      """)
  List<Listing> findStartingSoon(
      @Param("now") Instant now,
      @Param("city") String city,
      @Param("status") ListingStatus status);

  @Query(
      """
      SELECT l FROM Listing l JOIN l.category c
      WHERE c.slug = :slug
        AND l.status = :status
        AND l.listingExpiresAt > :now
      ORDER BY l.startTime ASC, l.discountedPriceCents ASC
      """)
  List<Listing> findActiveByCategorySlug(
      @Param("now") Instant now,
      @Param("slug") String slug,
      @Param("status") ListingStatus status);

  @Query(
      """
      SELECT l FROM Listing l
      WHERE l.id = :id
        AND l.status = :status
        AND l.listingExpiresAt > :now
      """)
  Optional<Listing> findActiveById(
      @Param("now") Instant now,
      @Param("id") UUID id,
      @Param("status") ListingStatus status);
}
