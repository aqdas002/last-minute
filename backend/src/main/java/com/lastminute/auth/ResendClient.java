package com.lastminute.auth;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin Resend REST client. If {@code app.resend.api-key} is empty (dev mode), this logs the
 * outgoing message instead of POSTing — so local devs can grab the magic-link URL from the console
 * without burning a real Resend send.
 */
@Component
public class ResendClient {

  private static final Logger LOG = LoggerFactory.getLogger(ResendClient.class);

  private final String apiKey;
  private final String from;
  private final RestClient http;

  public ResendClient(
      @Value("${app.resend.api-key:}") String apiKey,
      @Value("${app.resend.from:dev@local}") String from) {
    this.apiKey = apiKey;
    this.from = from;
    this.http = RestClient.builder().baseUrl("https://api.resend.com").build();
  }

  public void sendMagicLink(String to, String url) {
    if (apiKey == null || apiKey.isBlank()) {
      LOG.warn("[dev] would email {} -> magic link {}", to, url);
      return;
    }
    http
        .post()
        .uri("/emails")
        .header("Authorization", "Bearer " + apiKey)
        .body(
            Map.of(
                "from", from,
                "to", to,
                "subject", "Your sign-in link",
                "html",
                    "<p>Click to sign in to Last Minute:</p>"
                        + "<p><a href=\""
                        + url
                        + "\">"
                        + url
                        + "</a></p>"
                        + "<p>This link expires in 15 minutes and can only be used once.</p>"))
        .retrieve()
        .toBodilessEntity();
  }
}
