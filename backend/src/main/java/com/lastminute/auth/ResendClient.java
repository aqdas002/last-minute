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

  /** Spec §5 Flow 1 step 8b: confirmation email sent right after pending→confirmed. */
  public void sendBookingConfirmed(
      String to,
      String listingTitle,
      String startTimeFormatted,
      String redemptionCode,
      String providerName,
      String address) {
    if (apiKey == null || apiKey.isBlank()) {
      LOG.warn(
          "[dev] would email {} -> confirmed '{}' at {} (code {}, provider {}, addr {})",
          to,
          listingTitle,
          startTimeFormatted,
          redemptionCode,
          providerName,
          address);
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
                "subject", "Booking confirmed: " + listingTitle,
                "html",
                    "<p>You're booked for <strong>"
                        + listingTitle
                        + "</strong> with "
                        + providerName
                        + ".</p>"
                        + "<p>When: "
                        + startTimeFormatted
                        + "<br>"
                        + "Where: "
                        + (address == null ? "see provider for details" : address)
                        + "</p>"
                        + "<p>Show this code at the door:<br>"
                        + "<strong style=\"font-size:24px;letter-spacing:4px\">"
                        + redemptionCode
                        + "</strong></p>"
                        + "<p style=\"color:#666\">All sales final. Full refund if the provider"
                        + " doesn't honor your booking.</p>"))
        .retrieve()
        .toBodilessEntity();
  }

  /** Spec §5 Flow 1 step 9: T-1h booking reminder. */
  public void sendBookingReminder(
      String to, String listingTitle, String startTimeFormatted, String redemptionCode) {
    if (apiKey == null || apiKey.isBlank()) {
      LOG.warn(
          "[dev] would email {} -> reminder for '{}' at {} (code {})",
          to,
          listingTitle,
          startTimeFormatted,
          redemptionCode);
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
                "subject", "Reminder: " + listingTitle + " starts soon",
                "html",
                    "<p>Heads up — your booking starts at "
                        + startTimeFormatted
                        + ".</p>"
                        + "<p>Show this code at the door: <strong>"
                        + redemptionCode
                        + "</strong></p>"))
        .retrieve()
        .toBodilessEntity();
  }

  /** Spec §6.4: notify ops when a consumer files a refund request. */
  public void sendRefundRequestFiled(
      String adminEmail,
      String consumerEmail,
      String listingTitle,
      String reason,
      String details,
      String bookingId,
      int amountCents,
      String currency) {
    if (apiKey == null || apiKey.isBlank()) {
      LOG.warn(
          "[dev] would email {} -> refund request from {} for '{}' (reason={}, booking={}, amount={} {})",
          adminEmail,
          consumerEmail,
          listingTitle,
          reason,
          bookingId,
          amountCents,
          currency);
      return;
    }
    String body =
        "<p><strong>"
            + consumerEmail
            + "</strong> filed a refund request.</p>"
            + "<p>Listing: "
            + listingTitle
            + "<br>Reason: <strong>"
            + reason
            + "</strong><br>Amount: "
            + currency
            + " "
            + (amountCents / 100.0)
            + "<br>Booking id: <code>"
            + bookingId
            + "</code></p>"
            + (details == null || details.isBlank() ? "" : "<p><em>" + details + "</em></p>")
            + "<p>Action: refund via Stripe Dashboard; the resulting webhook will auto-close"
            + " this request.</p>";
    http
        .post()
        .uri("/emails")
        .header("Authorization", "Bearer " + apiKey)
        .body(
            Map.of(
                "from", from,
                "to", adminEmail,
                "subject", "Refund request: " + listingTitle,
                "html", body))
        .retrieve()
        .toBodilessEntity();
  }

  /** Spec §5 Flow 2 step 6: notify the provider once Stripe Connect KYC clears. */
  public void sendProviderLive(String to) {
    if (apiKey == null || apiKey.isBlank()) {
      LOG.warn("[dev] would email {} -> 'You're live on Last Minute'", to);
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
                "subject", "You're live on Last Minute",
                "html",
                    "<p>Your Stripe verification is complete.</p>"
                        + "<p>You can now publish listings and start taking bookings.</p>"))
        .retrieve()
        .toBodilessEntity();
  }
}
