package com.lastminute.bookings;

import com.lastminute.listings.Listing;
import com.lastminute.providers.Provider;
import com.lastminute.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "bookings")
public class Booking {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "listing_id", nullable = false)
  private Listing listing;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "consumer_id", nullable = false)
  private User consumer;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "provider_id", nullable = false)
  private Provider provider;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(nullable = false, columnDefinition = "booking_status")
  private BookingStatus status = BookingStatus.pending;

  @Column(name = "amount_paid_cents", nullable = false)
  private int amountPaidCents;

  @Column(name = "platform_fee_cents", nullable = false)
  private int platformFeeCents;

  @Column(name = "provider_payout_cents", nullable = false)
  private int providerPayoutCents;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(nullable = false, length = 3, columnDefinition = "char(3)")
  private String currency;

  @Column(name = "stripe_checkout_session_id", unique = true)
  private String stripeCheckoutSessionId;

  @Column(name = "stripe_payment_intent_id")
  private String stripePaymentIntentId;

  @Column(name = "pending_expires_at", nullable = false)
  private Instant pendingExpiresAt;

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "cancellation_reason", columnDefinition = "cancellation_reason")
  private CancellationReason cancellationReason;

  @Column(name = "redemption_code", nullable = false, length = 8, columnDefinition = "char(8)")
  @JdbcTypeCode(SqlTypes.CHAR)
  private String redemptionCode;

  @Column(name = "redeemed_at")
  private Instant redeemedAt;

  @Column(name = "reminder_sent_at")
  private Instant reminderSentAt;

  @Column(name = "refresh_count", nullable = false)
  private int refreshCount;

  @Column(name = "refresh_last_at")
  private Instant refreshLastAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // Getters / setters

  public UUID getId() { return id; }
  public Listing getListing() { return listing; }
  public void setListing(Listing l) { this.listing = l; }
  public User getConsumer() { return consumer; }
  public void setConsumer(User u) { this.consumer = u; }
  public Provider getProvider() { return provider; }
  public void setProvider(Provider p) { this.provider = p; }
  public BookingStatus getStatus() { return status; }
  public void setStatus(BookingStatus s) { this.status = s; }
  public int getAmountPaidCents() { return amountPaidCents; }
  public void setAmountPaidCents(int c) { this.amountPaidCents = c; }
  public int getPlatformFeeCents() { return platformFeeCents; }
  public void setPlatformFeeCents(int c) { this.platformFeeCents = c; }
  public int getProviderPayoutCents() { return providerPayoutCents; }
  public void setProviderPayoutCents(int c) { this.providerPayoutCents = c; }
  public String getCurrency() { return currency; }
  public void setCurrency(String c) { this.currency = c; }
  public String getStripeCheckoutSessionId() { return stripeCheckoutSessionId; }
  public void setStripeCheckoutSessionId(String s) { this.stripeCheckoutSessionId = s; }
  public String getStripePaymentIntentId() { return stripePaymentIntentId; }
  public void setStripePaymentIntentId(String s) { this.stripePaymentIntentId = s; }
  public Instant getPendingExpiresAt() { return pendingExpiresAt; }
  public void setPendingExpiresAt(Instant t) { this.pendingExpiresAt = t; }
  public Instant getConfirmedAt() { return confirmedAt; }
  public void setConfirmedAt(Instant t) { this.confirmedAt = t; }
  public Instant getCancelledAt() { return cancelledAt; }
  public void setCancelledAt(Instant t) { this.cancelledAt = t; }
  public CancellationReason getCancellationReason() { return cancellationReason; }
  public void setCancellationReason(CancellationReason r) { this.cancellationReason = r; }
  public String getRedemptionCode() { return redemptionCode; }
  public void setRedemptionCode(String c) { this.redemptionCode = c; }
  public Instant getRedeemedAt() { return redeemedAt; }
  public void setRedeemedAt(Instant t) { this.redeemedAt = t; }
  public Instant getReminderSentAt() { return reminderSentAt; }
  public void setReminderSentAt(Instant t) { this.reminderSentAt = t; }
  public int getRefreshCount() { return refreshCount; }
  public void setRefreshCount(int c) { this.refreshCount = c; }
  public Instant getRefreshLastAt() { return refreshLastAt; }
  public void setRefreshLastAt(Instant t) { this.refreshLastAt = t; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
