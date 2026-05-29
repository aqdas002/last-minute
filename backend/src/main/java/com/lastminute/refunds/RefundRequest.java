package com.lastminute.refunds;

import com.lastminute.bookings.Booking;
import com.lastminute.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

@Entity
@Table(name = "refund_requests")
public class RefundRequest {

  @Id @GeneratedValue private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "booking_id")
  private Booking booking;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "consumer_id")
  private User consumer;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(nullable = false)
  private RefundReason reason;

  @Column(columnDefinition = "TEXT")
  private String details;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(nullable = false)
  private RefundRequestStatus status = RefundRequestStatus.open;

  @Column(name = "admin_notes", columnDefinition = "TEXT")
  private String adminNotes;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
  private Instant updatedAt;

  public UUID getId() { return id; }
  public Booking getBooking() { return booking; }
  public void setBooking(Booking b) { this.booking = b; }
  public User getConsumer() { return consumer; }
  public void setConsumer(User u) { this.consumer = u; }
  public RefundReason getReason() { return reason; }
  public void setReason(RefundReason r) { this.reason = r; }
  public String getDetails() { return details; }
  public void setDetails(String d) { this.details = d; }
  public RefundRequestStatus getStatus() { return status; }
  public void setStatus(RefundRequestStatus s) { this.status = s; }
  public String getAdminNotes() { return adminNotes; }
  public void setAdminNotes(String n) { this.adminNotes = n; }
  public Instant getResolvedAt() { return resolvedAt; }
  public void setResolvedAt(Instant t) { this.resolvedAt = t; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
