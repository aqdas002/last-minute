package com.lastminute.listings;

import com.lastminute.categories.Category;
import com.lastminute.providers.Provider;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "listings")
public class Listing {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "provider_id", nullable = false)
  private Provider provider;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "category_id", nullable = false)
  private Category category;

  @Column(nullable = false)
  private String title;

  @Column private String description;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<String> images = new ArrayList<>();

  @Column(name = "original_price_cents", nullable = false)
  private int originalPriceCents;

  @Column(name = "discounted_price_cents", nullable = false)
  private int discountedPriceCents;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(nullable = false, length = 3, columnDefinition = "char(3)")
  private String currency;

  @Column(nullable = false)
  private int capacity = 1;

  @Column(name = "start_time", nullable = false)
  private Instant startTime;

  @Column(name = "end_time", nullable = false)
  private Instant endTime;

  @Column(name = "listing_expires_at", nullable = false)
  private Instant listingExpiresAt;

  @Column(nullable = false)
  private String timezone;

  @Column private String address;

  @Column private Double lat;

  @Column private Double lon;

  @Column private String city;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(nullable = false, columnDefinition = "listing_status")
  private ListingStatus status = ListingStatus.draft;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> metadata = new HashMap<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // Getters / setters

  public UUID getId() { return id; }
  public Provider getProvider() { return provider; }
  public void setProvider(Provider p) { this.provider = p; }
  public Category getCategory() { return category; }
  public void setCategory(Category c) { this.category = c; }
  public String getTitle() { return title; }
  public void setTitle(String t) { this.title = t; }
  public String getDescription() { return description; }
  public void setDescription(String d) { this.description = d; }
  public List<String> getImages() { return images; }
  public void setImages(List<String> i) { this.images = i; }
  public int getOriginalPriceCents() { return originalPriceCents; }
  public void setOriginalPriceCents(int c) { this.originalPriceCents = c; }
  public int getDiscountedPriceCents() { return discountedPriceCents; }
  public void setDiscountedPriceCents(int c) { this.discountedPriceCents = c; }
  public String getCurrency() { return currency; }
  public void setCurrency(String c) { this.currency = c; }
  public int getCapacity() { return capacity; }
  public void setCapacity(int c) { this.capacity = c; }
  public Instant getStartTime() { return startTime; }
  public void setStartTime(Instant t) { this.startTime = t; }
  public Instant getEndTime() { return endTime; }
  public void setEndTime(Instant t) { this.endTime = t; }
  public Instant getListingExpiresAt() { return listingExpiresAt; }
  public void setListingExpiresAt(Instant t) { this.listingExpiresAt = t; }
  public String getTimezone() { return timezone; }
  public void setTimezone(String t) { this.timezone = t; }
  public String getAddress() { return address; }
  public void setAddress(String a) { this.address = a; }
  public Double getLat() { return lat; }
  public void setLat(Double d) { this.lat = d; }
  public Double getLon() { return lon; }
  public void setLon(Double d) { this.lon = d; }
  public String getCity() { return city; }
  public void setCity(String c) { this.city = c; }
  public ListingStatus getStatus() { return status; }
  public void setStatus(ListingStatus s) { this.status = s; }
  public Map<String, Object> getMetadata() { return metadata; }
  public void setMetadata(Map<String, Object> m) { this.metadata = m; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
