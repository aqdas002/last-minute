package com.lastminute.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReturnToValidatorTest {

  private final ReturnToValidator v = new ReturnToValidator();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/",
        "/c/yoga",
        "/c/restaurants-and-bars",
        "/c/yoga?filter=tonight",
        "/c/yoga?filter=tonight&distance=5",
        "/bookings/abc12345-6789-4def-9012-3456789abcde",
        "/bookings/abc12345-6789-4def-9012-3456789abcde?from=email",
        "/book/abc12345-6789-4def-9012-3456789abcde",
        "/provider/dashboard",
        "/provider/onboarding",
        "/provider/bookings",
        "/provider/listings",
        "/provider/dashboard/settings",
        "/provider/listings?status=draft",
      })
  void allowed(String path) {
    assertThat(v.isAllowed(path)).as("expected ALLOWED: %s", path).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        // Open redirect
        "https://evil.com",
        "http://evil.com",
        "//evil.com",
        "//evil.com/foo",
        // Scheme injection
        "javascript:alert(1)",
        "data:text/html,<script>",
        "vbscript:msgbox(1)",
        // Path traversal
        "/c/../admin",
        "/c/%2e%2e/admin",
        "/bookings/%2F..%2Fadmin",
        // Backslash
        "/\\evil",
        "\\evil",
        // Empty / whitespace
        " ",
        // Unknown top-level path
        "/admin",
        "/admin/users",
        "/api/secret",
        "/foo",
        // Bookings with non-uuid
        "/bookings/not-a-uuid",
        "/bookings/abc.com",
        // Provider with unlisted subpath
        "/provider/secret",
        "/provider/admin-thing",
        // Fragment-only / multi-slash
        "#foo",
        "///",
      })
  void denied(String path) {
    assertThat(v.isAllowed(path)).as("expected DENIED: %s", path).isFalse();
  }

  @Test
  void empty_string_denied() {
    assertThat(v.isAllowed("")).isFalse();
  }

  @Test
  void null_denied() {
    assertThat(v.isAllowed(null)).isFalse();
  }

  @Test
  void safe_returns_path_for_allowed() {
    assertThat(v.safe("/c/yoga")).isEqualTo("/c/yoga");
  }

  @Test
  void safe_returns_slash_for_denied() {
    assertThat(v.safe("https://evil.com")).isEqualTo("/");
    assertThat(v.safe(null)).isEqualTo("/");
    assertThat(v.safe("")).isEqualTo("/");
  }
}
