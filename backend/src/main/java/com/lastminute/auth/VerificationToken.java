package com.lastminute.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "verification_tokens")
@IdClass(VerificationTokenId.class)
public class VerificationToken {

  @Id private String identifier;

  @Id
  @Column(unique = true)
  private String token;

  @Column(nullable = false)
  private OffsetDateTime expires;

  public String getIdentifier() { return identifier; }
  public void setIdentifier(String s) { this.identifier = s; }
  public String getToken() { return token; }
  public void setToken(String s) { this.token = s; }
  public OffsetDateTime getExpires() { return expires; }
  public void setExpires(OffsetDateTime t) { this.expires = t; }
}
