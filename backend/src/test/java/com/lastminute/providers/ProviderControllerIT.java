package com.lastminute.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lastminute.support.IntegrationTestBase;
import com.lastminute.users.UserRepository;
import com.lastminute.users.UserRole;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ProviderControllerIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private UserRepository users;
  @Autowired private ProviderRepository providers;

  private Map<String, Object> validBody(String email) {
    return Map.of(
        "email", email,
        "businessName", "Sunset Yoga Studio",
        "currency", "USD",
        "timezone", "America/New_York");
  }

  @Test
  void signup_creates_user_and_provider_with_pending_kyc_status() throws Exception {
    mvc.perform(
            post("/api/providers/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(validBody("studio@example.com"))))
        .andExpect(status().isAccepted());

    var user = users.findByEmail("studio@example.com").orElseThrow();
    assertThat(user.getRole()).isEqualTo(UserRole.provider);

    var provider = providers.findById(user.getId()).orElseThrow();
    assertThat(provider.getStatus()).isEqualTo(ProviderStatus.pending_kyc);
    assertThat(provider.getBusinessName()).isEqualTo("Sunset Yoga Studio");
    assertThat(provider.getCurrency()).isEqualTo("USD");
    assertThat(provider.getTimezone()).isEqualTo("America/New_York");
  }

  @Test
  void duplicate_email_returns_409() throws Exception {
    mvc.perform(
            post("/api/providers/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(validBody("dup@example.com"))))
        .andExpect(status().isAccepted());

    mvc.perform(
            post("/api/providers/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(validBody("dup@example.com"))))
        .andExpect(status().isConflict());
  }

  @Test
  void invalid_email_returns_400() throws Exception {
    Map<String, Object> body = new java.util.HashMap<>(validBody("not-an-email"));
    mvc.perform(
            post("/api/providers/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void currency_must_be_3_chars() throws Exception {
    Map<String, Object> body = new java.util.HashMap<>(validBody("curr@example.com"));
    body.put("currency", "EU");
    mvc.perform(
            post("/api/providers/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void timezone_must_not_be_blank() throws Exception {
    Map<String, Object> body = new java.util.HashMap<>(validBody("tz@example.com"));
    body.put("timezone", "");
    mvc.perform(
            post("/api/providers/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }
}
