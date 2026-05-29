package com.lastminute.webhooks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "payment_events")
public class PaymentEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /** Nullable — some events arrive without booking context (e.g. account.updated). */
  @Column(name = "booking_id")
  private UUID bookingId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "stripe_event_id", nullable = false, unique = true)
  private String stripeEventId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String payload;

  @CreationTimestamp
  @Column(name = "received_at", nullable = false, updatable = false)
  private Instant receivedAt;

  @Column(name = "processed_at")
  private Instant processedAt;

  @Column(name = "processing_error")
  private String processingError;

  public UUID getId() { return id; }
  public UUID getBookingId() { return bookingId; }
  public void setBookingId(UUID id) { this.bookingId = id; }
  public String getEventType() { return eventType; }
  public void setEventType(String t) { this.eventType = t; }
  public String getStripeEventId() { return stripeEventId; }
  public void setStripeEventId(String s) { this.stripeEventId = s; }
  public String getPayload() { return payload; }
  public void setPayload(String p) { this.payload = p; }
  public Instant getReceivedAt() { return receivedAt; }
  public Instant getProcessedAt() { return processedAt; }
  public void setProcessedAt(Instant t) { this.processedAt = t; }
  public String getProcessingError() { return processingError; }
  public void setProcessingError(String s) { this.processingError = s; }
}
