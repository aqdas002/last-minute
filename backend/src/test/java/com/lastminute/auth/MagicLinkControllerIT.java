package com.lastminute.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lastminute.support.IntegrationTestBase;
import com.lastminute.users.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class MagicLinkControllerIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private VerificationTokenRepository tokens;
  @Autowired private UserRepository users;
  @Autowired private ObjectMapper json;

  @Test
  void request_persists_a_token_and_returns_202() throws Exception {
    String body = json.writeValueAsString(Map.of("email", "alice@example.com"));

    mvc.perform(post("/api/auth/magic/request").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isAccepted());

    assertThat(tokens.count()).isEqualTo(1);
    assertThat(tokens.findAll().get(0).getIdentifier()).isEqualTo("alice@example.com");
  }

  @Test
  void request_rejects_invalid_email_with_400() throws Exception {
    String body = json.writeValueAsString(Map.of("email", "not-an-email"));

    mvc.perform(post("/api/auth/magic/request").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void consume_with_valid_token_creates_user_and_redirects_to_frontend() throws Exception {
    // Request a token so it lands in the DB.
    String body = json.writeValueAsString(Map.of("email", "bob@example.com"));
    mvc.perform(post("/api/auth/magic/request").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isAccepted());
    String token = tokens.findAll().get(0).getToken();

    mvc.perform(get("/api/auth/magic").param("token", token).param("return_to", "/c/yoga"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "http://localhost:5173/c/yoga"));

    assertThat(users.findByEmail("bob@example.com")).isPresent();
    assertThat(tokens.count()).isEqualTo(0); // single-use
  }

  @Test
  void consume_with_unknown_token_redirects_to_signin_with_error() throws Exception {
    mvc.perform(get("/api/auth/magic").param("token", "nope"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "http://localhost:5173/signin?error=not_found"));
  }

  @Test
  void consume_with_unsafe_return_to_falls_back_to_root() throws Exception {
    String body = json.writeValueAsString(Map.of("email", "evil@example.com"));
    mvc.perform(post("/api/auth/magic/request").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isAccepted());
    String token = tokens.findAll().get(0).getToken();

    mvc.perform(get("/api/auth/magic").param("token", token).param("return_to", "https://evil.com"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "http://localhost:5173/"));
  }
}
