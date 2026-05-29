package com.lastminute.webhooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lastminute.support.IntegrationTestBase;
import com.stripe.net.Webhook;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class WebhookControllerIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private PaymentEventRepository events;
  @Autowired private WebhookDeadLetterRepository deadLetter;

  @Value("${app.stripe.webhook-secret:}")
  private String webhookSecret;

  /**
   * Build a payload that Stripe's webhook parser will accept. The id + type fields are required
   * for {@code Event} to deserialize; the rest of the payload is up to us.
   */
  private static String validPayload(String id, String type) {
    return """
        {
          "id": "%s",
          "object": "event",
          "api_version": "2024-06-20",
          "created": 1700000000,
          "type": "%s",
          "data": { "object": { "id": "acct_TEST123", "object": "account",
                                "charges_enabled": true, "payouts_enabled": true } },
          "livemode": false,
          "pending_webhooks": 0,
          "request": { "id": null, "idempotency_key": null }
        }
        """.formatted(id, type);
  }

  private String sign(String payload) {
    long ts = Instant.now().getEpochSecond();
    String signed = ts + "." + payload;
    String sigHash = computeHmac(signed, webhookSecret);
    return "t=" + ts + ",v1=" + sigHash;
  }

  private static String computeHmac(String data, String secret) {
    try {
      var mac = javax.crypto.Mac.getInstance("HmacSHA256");
      mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      var sb = new StringBuilder();
      for (byte b : hmac) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void valid_signature_and_event_returns_200_and_persists() throws Exception {
    String payload = validPayload("evt_HAPPY", "account.updated");

    mvc.perform(
            post("/api/webhooks/stripe")
                .header("Stripe-Signature", sign(payload))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk());

    assertThat(events.findByStripeEventId("evt_HAPPY")).isPresent();
    assertThat(deadLetter.count()).isEqualTo(0);
  }

  @Test
  void invalid_signature_returns_400_and_persists_nothing() throws Exception {
    String payload = validPayload("evt_BADSIG", "account.updated");

    mvc.perform(
            post("/api/webhooks/stripe")
                .header("Stripe-Signature", "t=1,v1=deadbeef")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isBadRequest());

    assertThat(events.findByStripeEventId("evt_BADSIG")).isEmpty();
  }

  @Test
  void duplicate_event_returns_200_and_is_idempotent() throws Exception {
    String payload = validPayload("evt_DUPE", "account.updated");

    mvc.perform(
            post("/api/webhooks/stripe")
                .header("Stripe-Signature", sign(payload))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk());

    // Stripe retries; we should accept (200) but not create a second row.
    mvc.perform(
            post("/api/webhooks/stripe")
                .header("Stripe-Signature", sign(payload))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk());

    assertThat(events.count()).isEqualTo(1);
  }

  @Test
  void webhook_secret_is_validated_at_constructEvent_via_Stripe_SDK() throws Exception {
    // Sanity: the test setup actually uses a real signing secret; if we sign with the wrong
    // secret, Stripe's verifier rejects.
    String payload = validPayload("evt_WRONGSEC", "account.updated");
    String tamperedSig = "t=" + Instant.now().getEpochSecond() + ",v1=" + computeHmac(
        Instant.now().getEpochSecond() + "." + payload, "wrong-secret-xxx");

    mvc.perform(
            post("/api/webhooks/stripe")
                .header("Stripe-Signature", tamperedSig)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isBadRequest());
  }

  /** Verifies our use of Webhook.constructEvent matches what Stripe's SDK supports. */
  @Test
  void stripe_sdk_constructEvent_accepts_our_signed_payload() throws Exception {
    String payload = validPayload("evt_SDKCHECK", "account.updated");
    var ev = Webhook.constructEvent(payload, sign(payload), webhookSecret);
    assertThat(ev.getId()).isEqualTo("evt_SDKCHECK");
    assertThat(ev.getType()).isEqualTo("account.updated");
  }
}
