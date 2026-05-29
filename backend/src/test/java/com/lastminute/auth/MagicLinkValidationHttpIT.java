package com.lastminute.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.lastminute.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Real-HTTP-layer test (TestRestTemplate, not MockMvc) that exercises the full Spring Security
 * filter chain. M1 retro found that the MockMvc test passed but real curl returned 403 instead of
 * 400 on a Bean Validation failure. This test catches that class of mismatch.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MagicLinkValidationHttpIT extends IntegrationTestBase {

  @Autowired private TestRestTemplate http;

  @Test
  void invalid_email_returns_400_with_structured_body() {
    ResponseEntity<String> res =
        http.postForEntity(
            "/api/auth/magic/request", Map.of("email", "not-an-email"), String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(res.getBody()).contains("validation_failed");
    assertThat(res.getBody()).contains("email");
  }

  @Test
  void empty_body_returns_400() {
    ResponseEntity<String> res =
        http.postForEntity("/api/auth/magic/request", Map.of(), String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void valid_email_still_returns_202() {
    ResponseEntity<String> res =
        http.postForEntity(
            "/api/auth/magic/request", Map.of("email", "valid@example.com"), String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  }
}
