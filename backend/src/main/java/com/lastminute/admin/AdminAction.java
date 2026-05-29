package com.lastminute.admin;

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
@Table(name = "admin_actions")
public class AdminAction {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "actor_user_id", nullable = false)
  private UUID actorUserId;

  @Column(nullable = false)
  private String action;

  @Column(name = "target_id")
  private UUID targetId;

  @Column(nullable = false)
  private String reason;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String payload = "{}";

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public UUID getId() { return id; }
  public UUID getActorUserId() { return actorUserId; }
  public void setActorUserId(UUID id) { this.actorUserId = id; }
  public String getAction() { return action; }
  public void setAction(String s) { this.action = s; }
  public UUID getTargetId() { return targetId; }
  public void setTargetId(UUID t) { this.targetId = t; }
  public String getReason() { return reason; }
  public void setReason(String r) { this.reason = r; }
  public String getPayload() { return payload; }
  public void setPayload(String p) { this.payload = p; }
  public Instant getCreatedAt() { return createdAt; }
}
