package com.lastminute.auth;

import com.lastminute.users.UserRole;
import java.io.Serializable;
import java.util.UUID;

/**
 * Lightweight principal exposed to controllers via {@code @AuthenticationPrincipal}.
 *
 * <p>Implements {@link Serializable} so Spring Session JDBC can persist it in the
 * {@code SPRING_SESSION_ATTRIBUTES} table.
 *
 * <p>The custom {@code toString()} keeps {@code SPRING_SESSION.PRINCIPAL_NAME} under its
 * VARCHAR(100) limit — the default record-style toString includes all fields and overflows.
 */
public record CurrentUser(UUID id, String email, UserRole role) implements Serializable {

  @Override
  public String toString() {
    return email;
  }
}
