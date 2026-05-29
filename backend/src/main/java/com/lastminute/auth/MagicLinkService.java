package com.lastminute.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Magic-link auth flow per spec §3.3.
 *
 * <p>Request: generate a 32-byte URL-safe token, persist in {@code verification_tokens} with a
 * 15-minute expiry, email it to the user via {@link ResendClient}.
 *
 * <p>Consume: look up by token; reject if expired; otherwise delete (single-use) and return the
 * identifier the token was issued to.
 */
@Service
public class MagicLinkService {

  static final Duration TTL = Duration.ofMinutes(15);
  private static final SecureRandom RNG = new SecureRandom();

  private final VerificationTokenRepository tokens;
  private final ResendClient email;
  private final Clock clock;
  private final String baseUrl;

  public MagicLinkService(
      VerificationTokenRepository tokens,
      ResendClient email,
      Clock clock,
      @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
    this.tokens = tokens;
    this.email = email;
    this.clock = clock;
    this.baseUrl = baseUrl;
  }

  @Transactional
  public void request(String identifier, String returnTo) {
    String token = randomToken();
    OffsetDateTime expires =
        OffsetDateTime.ofInstant(Instant.now(clock).plus(TTL), ZoneOffset.UTC);
    VerificationToken vt = new VerificationToken();
    vt.setIdentifier(identifier);
    vt.setToken(token);
    vt.setExpires(expires);
    tokens.save(vt);
    String url =
        baseUrl + "/api/auth/magic?token=" + token + "&return_to=" + (returnTo == null ? "/" : returnTo);
    email.sendMagicLink(identifier, url);
  }

  /**
   * Returns the identifier the token was issued to. Deletes the token (single-use, including on
   * expiry-reject). {@code noRollbackFor} keeps the delete from being rolled back when we throw.
   */
  @Transactional(noRollbackFor = InvalidTokenException.class)
  public String consume(String token) {
    VerificationToken vt =
        tokens.findByToken(token).orElseThrow(() -> new InvalidTokenException("not_found"));
    if (vt.getExpires().toInstant().isBefore(Instant.now(clock))) {
      tokens.delete(vt);
      throw new InvalidTokenException("expired");
    }
    String identifier = vt.getIdentifier();
    tokens.delete(vt);
    return identifier;
  }

  private static String randomToken() {
    byte[] buf = new byte[32];
    RNG.nextBytes(buf);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
  }
}
