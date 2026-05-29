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
@Table(name = "webhook_dead_letter")
public class WebhookDeadLetter {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "stripe_event_id", nullable = false, unique = true)
  private String stripeEventId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String payload;

  @CreationTimestamp
  @Column(name = "first_failed_at", nullable = false, updatable = false)
  private Instant firstFailedAt;

  @Column(nullable = false)
  private int retries = 0;

  @Column(name = "last_error")
  private String lastError;

  public UUID getId() { return id; }
  public String getStripeEventId() { return stripeEventId; }
  public void setStripeEventId(String s) { this.stripeEventId = s; }
  public String getEventType() { return eventType; }
  public void setEventType(String s) { this.eventType = s; }
  public String getPayload() { return payload; }
  public void setPayload(String p) { this.payload = p; }
  public Instant getFirstFailedAt() { return firstFailedAt; }
  public int getRetries() { return retries; }
  public void setRetries(int r) { this.retries = r; }
  public String getLastError() { return lastError; }
  public void setLastError(String e) { this.lastError = e; }
}
