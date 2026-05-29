package com.lastminute.bookings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lastminute.categories.Category;
import com.lastminute.categories.CategoryRepository;
import com.lastminute.listings.Listing;
import com.lastminute.listings.ListingRepository;
import com.lastminute.listings.ListingStatus;
import com.lastminute.providers.Provider;
import com.lastminute.providers.ProviderRepository;
import com.lastminute.providers.ProviderStatus;
import com.lastminute.support.IntegrationTestBase;
import com.lastminute.users.User;
import com.lastminute.users.UserRepository;
import com.lastminute.users.UserRole;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class RefundAndChargebackHandlerIT extends IntegrationTestBase {

  @Autowired MockMvc mvc;
  @Autowired BookingRepository bookings;
  @Autowired UserRepository users;
  @Autowired ProviderRepository providers;
  @Autowired CategoryRepository categories;
  @Autowired ListingRepository listings;

  @Value("${app.stripe.webhook-secret:}")
  private String webhookSecret;

  private Booking seedBooking(BookingStatus status, String paymentIntentId) {
    User pu = new User();
    pu.setEmail("p-" + UUID.randomUUID() + "@x");
    pu.setRole(UserRole.provider);
    pu = users.save(pu);
    Provider provider = new Provider();
    provider.setId(pu.getId());
    provider.setBusinessName("Studio");
    provider.setCurrency("USD");
    provider.setTimezone("America/New_York");
    provider.setCountry("US");
    provider.setCity("New York");
    provider.setStatus(ProviderStatus.active);
    provider.setStripeChargesEnabled(true);
    provider = providers.save(provider);

    Category c = new Category();
    c.setSlug("c-" + UUID.randomUUID());
    c.setName("Cat");
    c = categories.save(c);

    Listing l = new Listing();
    l.setProvider(provider);
    l.setCategory(c);
    l.setTitle("L");
    l.setOriginalPriceCents(10000);
    l.setDiscountedPriceCents(8000);
    l.setCurrency("USD");
    l.setCapacity(1);
    Instant start = Instant.now().plus(2, ChronoUnit.HOURS);
    l.setStartTime(start);
    l.setEndTime(start.plus(1, ChronoUnit.HOURS));
    l.setListingExpiresAt(start.minus(10, ChronoUnit.MINUTES));
    l.setTimezone("America/New_York");
    l.setStatus(ListingStatus.active);
    l.setImages(new ArrayList<>());
    l = listings.save(l);

    User cu = new User();
    cu.setEmail("c-" + UUID.randomUUID() + "@x");
    cu.setRole(UserRole.consumer);
    cu = users.save(cu);

    Booking b = new Booking();
    b.setListing(l);
    b.setProvider(provider);
    b.setConsumer(cu);
    b.setStatus(status);
    b.setAmountPaidCents(8000);
    b.setPlatformFeeCents(1200);
    b.setProviderPayoutCents(6800);
    b.setCurrency("USD");
    b.setPendingExpiresAt(Instant.now().plusSeconds(2100));
    b.setRedemptionCode("R" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 7).toUpperCase());
    b.setStripePaymentIntentId(paymentIntentId);
    if (status == BookingStatus.confirmed) b.setConfirmedAt(Instant.now());
    return bookings.save(b);
  }

  private static String refundPayload(String eventId, String paymentIntentId) {
    return ("""
        {"id":"%s","object":"event","api_version":"2024-06-20","created":1700000000,
         "type":"charge.refunded","livemode":false,"pending_webhooks":0,
         "request":{"id":null,"idempotency_key":null},
         "data":{"object":{"id":"ch_TEST","object":"charge","payment_intent":"%s"}}}
        """).formatted(eventId, paymentIntentId);
  }

  private static String disputePayload(String eventId, String paymentIntentId) {
    return ("""
        {"id":"%s","object":"event","api_version":"2024-06-20","created":1700000000,
         "type":"charge.dispute.created","livemode":false,"pending_webhooks":0,
         "request":{"id":null,"idempotency_key":null},
         "data":{"object":{"id":"dp_TEST","object":"dispute","payment_intent":"%s"}}}
        """).formatted(eventId, paymentIntentId);
  }

  private String sign(String payload) {
    long ts = Instant.now().getEpochSecond();
    String signed = ts + "." + payload;
    try {
      var mac = javax.crypto.Mac.getInstance("HmacSHA256");
      mac.init(new javax.crypto.spec.SecretKeySpec(
          webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] h = mac.doFinal(signed.getBytes(StandardCharsets.UTF_8));
      var sb = new StringBuilder();
      for (byte b : h) sb.append(String.format("%02x", b));
      return "t=" + ts + ",v1=" + sb;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void refund_event_moves_confirmed_to_cancelled_with_refund_reason() throws Exception {
    Booking b = seedBooking(BookingStatus.confirmed, "pi_REFUND1");
    String payload = refundPayload("evt_REF1", "pi_REFUND1");

    mvc.perform(
            post("/api/webhooks/stripe")
                .header("Stripe-Signature", sign(payload))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk());

    await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
      Booking after = bookings.findById(b.getId()).orElseThrow();
      assertThat(after.getStatus()).isEqualTo(BookingStatus.cancelled);
      assertThat(after.getCancellationReason()).isEqualTo(CancellationReason.refund);
    });
  }

  @Test
  void refund_event_moves_completed_to_cancelled() throws Exception {
    Booking b = seedBooking(BookingStatus.completed, "pi_REFUND2");
    String payload = refundPayload("evt_REF2", "pi_REFUND2");

    mvc.perform(
            post("/api/webhooks/stripe")
                .header("Stripe-Signature", sign(payload))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk());

    await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
      Booking after = bookings.findById(b.getId()).orElseThrow();
      assertThat(after.getStatus()).isEqualTo(BookingStatus.cancelled);
      assertThat(after.getCancellationReason()).isEqualTo(CancellationReason.refund);
    });
  }

  @Test
  void dispute_event_moves_confirmed_to_cancelled_with_chargeback_reason() throws Exception {
    Booking b = seedBooking(BookingStatus.confirmed, "pi_DISPUTE1");
    String payload = disputePayload("evt_DIS1", "pi_DISPUTE1");

    mvc.perform(
            post("/api/webhooks/stripe")
                .header("Stripe-Signature", sign(payload))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk());

    await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
      Booking after = bookings.findById(b.getId()).orElseThrow();
      assertThat(after.getStatus()).isEqualTo(BookingStatus.cancelled);
      assertThat(after.getCancellationReason()).isEqualTo(CancellationReason.chargeback);
    });
  }

  @Test
  void refund_for_unknown_payment_intent_is_silently_ignored() throws Exception {
    String payload = refundPayload("evt_REFGHOST", "pi_DOES_NOT_EXIST");
    mvc.perform(
            post("/api/webhooks/stripe")
                .header("Stripe-Signature", sign(payload))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk());
  }
}
