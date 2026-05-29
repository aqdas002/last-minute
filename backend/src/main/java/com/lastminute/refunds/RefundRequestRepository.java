package com.lastminute.refunds;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, UUID> {

  @Query(
      """
      SELECT r FROM RefundRequest r
      WHERE r.booking.id = :bookingId AND r.status = :status
      """)
  Optional<RefundRequest> findOpenForBooking(
      @Param("bookingId") UUID bookingId, @Param("status") RefundRequestStatus status);

  List<RefundRequest> findAllByConsumer_IdOrderByCreatedAtDesc(UUID consumerId);
}
