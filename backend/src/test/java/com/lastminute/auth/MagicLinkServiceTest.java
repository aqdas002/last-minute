package com.lastminute.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lastminute.support.IntegrationTestBase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import(MagicLinkServiceTest.FixedClock.class)
class MagicLinkServiceTest extends IntegrationTestBase {

  static final Instant T0 = Instant.parse("2026-06-01T12:00:00Z");

  @TestConfiguration
  static class FixedClock {
    @Bean
    @Primary
    Clock testClock() {
      return Clock.fixed(T0, ZoneOffset.UTC);
    }
  }

  @Autowired private MagicLinkService magic;
  @Autowired private VerificationTokenRepository tokens;

  @Test
  void request_persists_a_token_with_15_minute_expiry() {
    magic.request("user@example.com", "/");
    assertThat(tokens.count()).isEqualTo(1);
    var vt = tokens.findAll().get(0);
    assertThat(vt.getIdentifier()).isEqualTo("user@example.com");
    assertThat(vt.getExpires().toInstant()).isEqualTo(T0.plus(Duration.ofMinutes(15)));
  }

  @Test
  void consume_returns_identifier_and_deletes_token() {
    magic.request("u@x.com", "/");
    String token = tokens.findAll().get(0).getToken();
    String email = magic.consume(token);
    assertThat(email).isEqualTo("u@x.com");
    assertThat(tokens.count()).isEqualTo(0);
  }

  @Test
  void consume_rejects_unknown_token() {
    assertThatThrownBy(() -> magic.consume("nope"))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessage("not_found");
  }

  @Test
  void consume_rejects_and_deletes_expired_token() {
    magic.request("expire@x.com", "/");
    String token = tokens.findAll().get(0).getToken();
    var vt = tokens.findByToken(token).orElseThrow();
    vt.setExpires(vt.getExpires().minusMinutes(60));
    tokens.save(vt);

    assertThatThrownBy(() -> magic.consume(token)).hasMessage("expired");
    assertThat(tokens.count()).isEqualTo(0);
  }

  @Test
  void second_consume_fails() {
    magic.request("once@x.com", "/");
    String token = tokens.findAll().get(0).getToken();
    magic.consume(token);
    assertThatThrownBy(() -> magic.consume(token)).hasMessage("not_found");
  }
}
