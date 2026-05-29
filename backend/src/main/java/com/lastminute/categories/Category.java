package com.lastminute.categories;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "categories")
public class Category {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true)
  private String slug;

  @Column(nullable = false)
  private String name;

  @Column(name = "icon_name")
  private String iconName;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id")
  private Category parent;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(nullable = false)
  private boolean active = true;

  /**
   * Spec §5 Flow 4: no-show auto-refund grace window (per-category). Stored as Postgres INTERVAL.
   * Hibernate maps it as a string in the form {@code "2 hours"} / {@code "1 hour"} / {@code "4
   * hours"}.
   */
  @Column(name = "no_show_grace_interval", nullable = false, columnDefinition = "interval")
  @ColumnTransformer(write = "?::interval", read = "no_show_grace_interval::text")
  private String noShowGraceInterval = "2 hours";

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  // Getters / setters
  public UUID getId() { return id; }
  public String getSlug() { return slug; }
  public void setSlug(String s) { this.slug = s; }
  public String getName() { return name; }
  public void setName(String n) { this.name = n; }
  public String getIconName() { return iconName; }
  public void setIconName(String i) { this.iconName = i; }
  public Category getParent() { return parent; }
  public void setParent(Category p) { this.parent = p; }
  public int getDisplayOrder() { return displayOrder; }
  public void setDisplayOrder(int d) { this.displayOrder = d; }
  public boolean isActive() { return active; }
  public void setActive(boolean a) { this.active = a; }
  public String getNoShowGraceInterval() { return noShowGraceInterval; }
  public void setNoShowGraceInterval(String s) { this.noShowGraceInterval = s; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
