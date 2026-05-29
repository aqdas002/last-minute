package com.lastminute.auth;

import com.lastminute.users.UserRole;
import java.io.Serializable;
import java.util.UUID;

/**
 * Lightweight principal exposed to controllers via {@code @AuthenticationPrincipal}.
 *
 * <p>Implements {@link Serializable} so Spring Session JDBC can persist it in the
 * {@code SPRING_SESSION_ATTRIBUTES} table.
 */
public record CurrentUser(UUID id, String email, UserRole role) implements Serializable {}
