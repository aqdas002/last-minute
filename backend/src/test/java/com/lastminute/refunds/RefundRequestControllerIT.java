package com.lastminute.refunds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lastminute.auth.CurrentUser;
import com.lastminute.bookings.Booking;
import com.lastminute.bookings.BookingRepository;
import com.lastminute.bookings.BookingStatus;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@AutoConfigureMockMvc
class RefundRequestControllerIT extends IntegrationTestBase {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired BookingRepository bookings;
  @Autowired UserRepository users;
  @Autowired ProviderRepository providers;
  @Autowired CategoryRepository categories;
  @Autowired ListingRepository listings;
  @Autowired RefundRequestRepository requests;

  @Value("${app.stripe.webhook-secret:}")
  private String webhookSecret;

  private record Seeded(Booking booking, CurrentUser consumerPrincipal, User otherConsumer) {}

  private Seeded seedConfirmed(String paymentIntentId) {
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
    l.setTitle("Yoga");
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

    User consumer = new User();
    consumer.setEmail("c-" + UUID.randomUUID() + "@x");
    consumer.setRole(UserRole.consumer);
    consumer = users.save(consumer);

    User other = new User();
    other.setEmail("c-other-" + UUID.randomUUID() + "@x");
    other.setRole(UserRole.consumer);
    other = users.save(other);

    Booking b = new Booking();
    b.setListing(l);
    b.setProvider(provider);
    b.setConsumer(consumer);
    b.setStatus(BookingStatus.confirmed);
    b.setAmountPaidCents(8000);
    b.setPlatformFeeCents(1200);
    b.setProviderPayoutCents(6800);
    b.setCurrency("USD");
    b.setPendingExpiresAt(Instant.now().plusSeconds(2100));
    b.setRedemptionCode("R" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 7).toUpperCase());
    if (paymentIntentId != null) b.setStripePaymentIntentId(paymentIntentId);
    b.setConfirmedAt(Instant.now());
    b = bookings.save(b);

    return new Seeded(b, new CurrentUser(consumer.getId(), consumer.getEmail(), UserRole.consumer), other);
  }

  private RequestPostProcessor as(CurrentUser u) {
    Authentication a =
        new UsernamePasswordAuthenticationToken(
            u, null, List.of(new SimpleGrantedAuthority("ROLE_CONSUMER")));
    return authentication(a);
  }

  @Test
  void file_happy_path_creates_open_request() throws Exception {
    Seeded s = seedConfirmed(null);
    var body = Map.of("reason", "provider_no_show", "details", "Was a no-show");

    mvc.perform(
            post("/api/bookings/{id}/refund-request", s.booking().getId())
                .with(as(s.consumerPrincipal()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("open"));

    assertThat(requests.findOpenForBooking(s.booking().getId(), RefundRequestStatus.open)).isPresent();
  }

  @Test
  void file_twice_returns_existing_open_request() throws Exception {
    Seeded s = seedConfirmed(null);
    var body = Map.of("reason", "quality_issue", "details", "Misleading");

    mvc.perform(
            post("/api/bookings/{id}/refund-request", s.booking().getId())
                .with(as(s.consumerPrincipal()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
        .andExpect(status().isOk());

    UUID firstId =
        requests.findOpenForBooking(s.booking().getId(), RefundRequestStatus.open).orElseThrow().getId();

    mvc.perform(
            post("/api/bookings/{id}/refund-request", s.booking().getId())
                .with(as(s.consumerPrincipal()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requestId").value(firstId.toString()));
  }

  @Test
  void file_against_pending_returns_409_NOT_PAID_YET() throws Exception {
    Seeded s = seedConfirmed(null);
    Booking b = bookings.findById(s.booking().getId()).orElseThrow();
    b.setStatus(BookingStatus.pending);
    bookings.save(b);

    mvc.perform(
            post("/api/bookings/{id}/refund-request", s.booking().getId())
                .with(as(s.consumerPrincipal()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("reason", "provider_no_show"))))
        .andExpect(status().isConflict());
  }

  @Test
  void file_against_cancelled_returns_409_ALREADY_REFUNDED() throws Exception {
    Seeded s = seedConfirmed(null);
    Booking b = bookings.findById(s.booking().getId()).orElseThrow();
    b.setStatus(BookingStatus.cancelled);
    bookings.save(b);

    mvc.perform(
            post("/api/bookings/{id}/refund-request", s.booking().getId())
                .with(as(s.consumerPrincipal()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("reason", "provider_no_show"))))
        .andExpect(status().isConflict());
  }

  @Test
  void file_for_someone_elses_booking_returns_404() throws Exception {
    Seeded s = seedConfirmed(null);
    CurrentUser intruder =
        new CurrentUser(s.otherConsumer().getId(), s.otherConsumer().getEmail(), UserRole.consumer);

    mvc.perform(
            post("/api/bookings/{id}/refund-request", s.booking().getId())
                .with(as(intruder))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("reason", "provider_no_show"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void charge_refunded_webhook_auto_closes_open_request() throws Exception {
    Seeded s = seedConfirmed("pi_AUTO_CLOSE");

    // File a request first
    mvc.perform(
            post("/api/bookings/{id}/refund-request", s.booking().getId())
                .with(as(s.consumerPrincipal()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("reason", "provider_no_show"))))
        .andExpect(status().isOk());

    UUID requestId =
        requests.findOpenForBooking(s.booking().getId(), RefundRequestStatus.open).orElseThrow().getId();

    // Now drive a charge.refunded webhook for the same payment intent
    String payload =
        ("""
         {"id":"evt_AUTO_CLOSE","object":"event","api_version":"2024-06-20","created":1700000000,
          "type":"charge.refunded","livemode":false,"pending_webhooks":0,
          "request":{"id":null,"idempotency_key":null},
          "data":{"object":{"id":"ch_TEST","object":"charge","payment_intent":"%s"}}}
         """).formatted("pi_AUTO_CLOSE");

    mvc.perform(
            post("/api/webhooks/stripe")
                .header("Stripe-Signature", sign(payload))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk());

    await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
      RefundRequest r = requests.findById(requestId).orElseThrow();
      assertThat(r.getStatus()).isEqualTo(RefundRequestStatus.auto_resolved);
      assertThat(r.getResolvedAt()).isNotNull();
    });
  }

  @Test
  void mine_endpoint_returns_only_caller_requests() throws Exception {
    Seeded s = seedConfirmed(null);
    mvc.perform(
            post("/api/bookings/{id}/refund-request", s.booking().getId())
                .with(as(s.consumerPrincipal()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("reason", "duplicate_charge"))))
        .andExpect(status().isOk());

    mvc.perform(get("/api/bookings/{id}/refund-request", s.booking().getId()).with(as(s.consumerPrincipal())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].reason").value("duplicate_charge"));
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
}
