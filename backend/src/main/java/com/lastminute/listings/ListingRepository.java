package com.lastminute.listings;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

  List<Listing> findAllByProviderIdOrderByStartTimeAsc(UUID providerId);


  /**
   * Consumer-facing "starting soon" feed. Strict {@code listing_expires_at > now} (belt-and-braces
   * per spec §2 caching). Optional city filter. {@code JOIN FETCH} eagerly loads category +
   * provider so the DTO serializer doesn't trip {@code LazyInitializationException}.
   */
  @Query(
      """
      SELECT l FROM Listing l
        JOIN FETCH l.category
        JOIN FETCH l.provider
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
      SELECT l FROM Listing l
        JOIN FETCH l.category c
        JOIN FETCH l.provider
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
        JOIN FETCH l.category
        JOIN FETCH l.provider
      WHERE l.id = :id
        AND l.status = :status
        AND l.listingExpiresAt > :now
      """)
  Optional<Listing> findActiveById(
      @Param("now") Instant now,
      @Param("id") UUID id,
      @Param("status") ListingStatus status);

  /**
   * Keyword search across title + description. Case-insensitive substring match. Bounded by
   * {@code listing_expires_at > now} and optional filters. Small dataset for MVP — Postgres
   * sequential scan is fine; introduce {@code pg_trgm} GIN index when result counts justify it.
   */
  @Query(
      """
      SELECT l FROM Listing l
        JOIN FETCH l.category c
        JOIN FETCH l.provider
      WHERE l.status = :status
        AND l.listingExpiresAt > :now
        AND (:city IS NULL OR l.city = :city)
        AND (:slug IS NULL OR c.slug = :slug)
        AND (
          LOWER(l.title) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(COALESCE(l.description, '')) LIKE LOWER(CONCAT('%', :q, '%'))
        )
      ORDER BY l.startTime ASC, l.discountedPriceCents ASC
      """)
  List<Listing> search(
      @Param("now") Instant now,
      @Param("q") String q,
      @Param("city") String city,
      @Param("slug") String slug,
      @Param("status") ListingStatus status);
}
