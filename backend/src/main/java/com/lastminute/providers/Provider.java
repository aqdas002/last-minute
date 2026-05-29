package com.lastminute.providers;

import com.lastminute.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "providers")
public class Provider {

  /** PK = users.id (1:1). Not auto-generated; set explicitly from the existing User. */
  @Id private UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "id", insertable = false, updatable = false)
  private User user;

  @Column(name = "business_name", nullable = false)
  private String businessName;

  @Column(name = "business_description")
  private String businessDescription;

  @Column(name = "contact_phone")
  private String contactPhone;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(nullable = false, length = 3, columnDefinition = "char(3)")
  private String currency;

  @Column(nullable = false)
  private String timezone;

  @Column(name = "stripe_account_id", unique = true)
  private String stripeAccountId;

  @Column(name = "stripe_onboarding_complete", nullable = false)
  private boolean stripeOnboardingComplete;

  @Column(name = "stripe_charges_enabled", nullable = false)
  private boolean stripeChargesEnabled;

  @Column(name = "stripe_payouts_enabled", nullable = false)
  private boolean stripePayoutsEnabled;

  @Column(name = "default_address")
  private String defaultAddress;

  @Column(name = "default_lat")
  private Double defaultLat;

  @Column(name = "default_lon")
  private Double defaultLon;

  @Column private String city;

  @Column private String country;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(nullable = false, columnDefinition = "provider_status")
  private ProviderStatus status = ProviderStatus.pending_kyc;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  // Getters / setters

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public User getUser() { return user; }
  public String getBusinessName() { return businessName; }
  public void setBusinessName(String s) { this.businessName = s; }
  public String getBusinessDescription() { return businessDescription; }
  public void setBusinessDescription(String s) { this.businessDescription = s; }
  public String getContactPhone() { return contactPhone; }
  public void setContactPhone(String s) { this.contactPhone = s; }
  public String getCurrency() { return currency; }
  public void setCurrency(String s) { this.currency = s; }
  public String getTimezone() { return timezone; }
  public void setTimezone(String s) { this.timezone = s; }
  public String getStripeAccountId() { return stripeAccountId; }
  public void setStripeAccountId(String s) { this.stripeAccountId = s; }
  public boolean isStripeOnboardingComplete() { return stripeOnboardingComplete; }
  public void setStripeOnboardingComplete(boolean b) { this.stripeOnboardingComplete = b; }
  public boolean isStripeChargesEnabled() { return stripeChargesEnabled; }
  public void setStripeChargesEnabled(boolean b) { this.stripeChargesEnabled = b; }
  public boolean isStripePayoutsEnabled() { return stripePayoutsEnabled; }
  public void setStripePayoutsEnabled(boolean b) { this.stripePayoutsEnabled = b; }
  public String getDefaultAddress() { return defaultAddress; }
  public void setDefaultAddress(String s) { this.defaultAddress = s; }
  public Double getDefaultLat() { return defaultLat; }
  public void setDefaultLat(Double d) { this.defaultLat = d; }
  public Double getDefaultLon() { return defaultLon; }
  public void setDefaultLon(Double d) { this.defaultLon = d; }
  public String getCity() { return city; }
  public void setCity(String s) { this.city = s; }
  public String getCountry() { return country; }
  public void setCountry(String s) { this.country = s; }
  public ProviderStatus getStatus() { return status; }
  public void setStatus(ProviderStatus s) { this.status = s; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
