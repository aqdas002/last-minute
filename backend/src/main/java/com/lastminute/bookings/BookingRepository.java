package com.lastminute.bookings;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

  /** Fast-path lookup for the double-tap / resume-existing-pending case. */
  @Query(
      """
      SELECT b FROM Booking b
      WHERE b.listing.id = :listingId
        AND b.consumer.id = :consumerId
        AND b.status IN :statuses
      """)
  Optional<Booking> findActiveForConsumer(
      @Param("listingId") UUID listingId,
      @Param("consumerId") UUID consumerId,
      @Param("statuses") List<BookingStatus> statuses);

  /** Capacity check inside the booking transaction. */
  @Query(
      """
      SELECT COUNT(b) FROM Booking b
      WHERE b.listing.id = :listingId
        AND b.status IN :statuses
      """)
  long countByListingAndStatuses(
      @Param("listingId") UUID listingId, @Param("statuses") List<BookingStatus> statuses);

  Optional<Booking> findByStripeCheckoutSessionId(String sessionId);

  Optional<Booking> findByStripePaymentIntentId(String paymentIntentId);

  @Query(
      """
      SELECT b FROM Booking b
      WHERE b.redemptionCode = :code
        AND b.provider.id = :providerId
        AND b.status IN :statuses
      """)
  Optional<Booking> findActiveByCodeAndProvider(
      @Param("code") String code,
      @Param("providerId") UUID providerId,
      @Param("statuses") List<BookingStatus> statuses);

  /** Sweeper finder: expired pending bookings. */
  @Query(
      """
      SELECT b FROM Booking b
      WHERE b.status = com.lastminute.bookings.BookingStatus.pending
        AND b.pendingExpiresAt < :now
      """)
  List<Booking> findExpiredPending(@Param("now") Instant now);

  /** Tightened M3 predicate for provider material-edit + currency-self-correct. */
  boolean existsByListing_IdAndStatusIn(UUID listingId, List<BookingStatus> statuses);

  boolean existsByProvider_Id(UUID providerId);

  /** Reminder sweeper: confirmed bookings starting in [now, now+1h] without reminder. */
  @Query(
      """
      SELECT b FROM Booking b
        JOIN FETCH b.listing l
      WHERE b.status = com.lastminute.bookings.BookingStatus.confirmed
        AND b.reminderSentAt IS NULL
        AND l.startTime BETWEEN :from AND :to
      """)
  List<Booking> findRemindersDue(@Param("from") Instant from, @Param("to") Instant to);

  /** Use the explicit FOR UPDATE lock for the listing row inside reserveSpot. Repository-level
   *  finder so the lock is acquired by JPA, not by hand-rolled SQL. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT l FROM Listing l WHERE l.id = :id")
  Optional<com.lastminute.listings.Listing> lockListingForUpdate(@Param("id") UUID id);

  /** Step 4b helper — conditional UPDATE returns affected-row count; 0 = sweeper raced us. */
  @Modifying
  @Query(
      """
      UPDATE Booking b SET b.stripeCheckoutSessionId = :sessionId
      WHERE b.id = :id AND b.status = com.lastminute.bookings.BookingStatus.pending
      """)
  int attachCheckoutSession(@Param("id") UUID id, @Param("sessionId") String sessionId);

  List<Booking> findAllByConsumer_IdOrderByCreatedAtDesc(UUID consumerId);

  /** Provider /today: confirmed bookings whose listing start_time falls in [from, to). */
  @Query(
      """
      SELECT b FROM Booking b
        JOIN FETCH b.listing l
        JOIN FETCH b.consumer
      WHERE b.provider.id = :providerId
        AND b.status = :status
        AND l.startTime >= :from AND l.startTime < :to
      ORDER BY l.startTime ASC
      """)
  List<Booking> findTodaysConfirmed(
      @Param("providerId") UUID providerId,
      @Param("status") BookingStatus status,
      @Param("from") Instant from,
      @Param("to") Instant to);

  /** Provider /all: most recent bookings, any status. */
  @Query(
      """
      SELECT b FROM Booking b
        JOIN FETCH b.listing l
        JOIN FETCH b.consumer
      WHERE b.provider.id = :providerId
      ORDER BY b.createdAt DESC
      """)
  List<Booking> findRecentByProvider(@Param("providerId") UUID providerId);
}
