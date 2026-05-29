package com.lastminute.auth;

import java.io.Serializable;
import java.util.Objects;

/** Composite-key id for {@link VerificationToken}. */
public class VerificationTokenId implements Serializable {

  private String identifier;
  private String token;

  public VerificationTokenId() {}

  public VerificationTokenId(String identifier, String token) {
    this.identifier = identifier;
    this.token = token;
  }

  public String getIdentifier() { return identifier; }
  public void setIdentifier(String s) { this.identifier = s; }
  public String getToken() { return token; }
  public void setToken(String s) { this.token = s; }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof VerificationTokenId other)) return false;
    return Objects.equals(identifier, other.identifier) && Objects.equals(token, other.token);
  }

  @Override public int hashCode() { return Objects.hash(identifier, token); }
}
