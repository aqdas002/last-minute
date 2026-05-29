package com.lastminute.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lastminute.admin.AdminActionRepository;
import com.lastminute.auth.CurrentUser;
import com.lastminute.bookings.Booking;
import com.lastminute.bookings.BookingRepository;
import com.lastminute.bookings.BookingStatus;
import com.lastminute.categories.Category;
import com.lastminute.categories.CategoryRepository;
import com.lastminute.listings.Listing;
import com.lastminute.listings.ListingRepository;
import com.lastminute.listings.ListingStatus;
import com.lastminute.support.IntegrationTestBase;
import com.lastminute.users.User;
import com.lastminute.users.UserRepository;
import com.lastminute.users.UserRole;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.lastminute.users.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@AutoConfigureMockMvc
class CurrencyToolsIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private UserRepository users;
  @Autowired private ProviderRepository providers;
  @Autowired private CategoryRepository categories;
  @Autowired private ListingRepository listings;
  @Autowired private BookingRepository bookings;
  @Autowired private AdminActionRepository audit;

  private CurrentUser providerPrincipal;

  @BeforeEach
  void seed() {
    User u = new User();
    u.setEmail("studio@example.com");
    u.setRole(UserRole.provider);
    u = users.save(u);

    Provider p = new Provider();
    p.setId(u.getId());
    p.setBusinessName("Studio");
    p.setCurrency("USD");
    p.setTimezone("America/New_York");
    p.setCountry("US");
    p.setCity("New York");
    p.setStatus(ProviderStatus.active);
    p.setStripeChargesEnabled(true);
    providers.save(p);

    providerPrincipal = new CurrentUser(u.getId(), u.getEmail(), UserRole.provider);
  }

  private RequestPostProcessor asProvider() {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            providerPrincipal,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_PROVIDER")));
    return authentication(auth);
  }

  private RequestPostProcessor asAdmin() {
    User a = new User();
    a.setEmail("admin-" + UUID.randomUUID() + "@local");
    a.setRole(UserRole.admin);
    a = users.save(a);
    CurrentUser adminPrincipal = new CurrentUser(a.getId(), a.getEmail(), UserRole.admin);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            adminPrincipal,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    return authentication(auth);
  }

  private Listing seedListing() {
    Category c = new Category();
    c.setSlug("yoga");
    c.setName("Yoga");
    c = categories.save(c);

    Provider p = providers.findById(providerPrincipal.id()).orElseThrow();
    Listing l = new Listing();
    l.setProvider(p);
    l.setCategory(c);
    l.setTitle("Test");
    l.setOriginalPriceCents(10000);
    l.setDiscountedPriceCents(8000);
    l.setCurrency("USD");
    l.setCapacity(1);
    Instant start = Instant.now().plusSeconds(3600);
    l.setStartTime(start);
    l.setEndTime(start.plusSeconds(3600));
    l.setListingExpiresAt(start.minusSeconds(600));
    l.setTimezone("America/New_York");
    l.setStatus(ListingStatus.draft);
    l.setImages(new ArrayList<>());
    return listings.save(l);
  }

  @Test
  void provider_can_self_correct_currency_before_any_listing() throws Exception {
    mvc.perform(
            patch("/api/providers/me/settings/currency")
                .with(asProvider())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currency", "EUR"))))
        .andExpect(status().isNoContent());

    assertThat(providers.findById(providerPrincipal.id()).orElseThrow().getCurrency())
        .isEqualTo("EUR");
  }

  @Test
  void self_correct_rejected_once_a_booking_exists() throws Exception {
    // M3 tightened the predicate: it's now "any booking" not "any listing".
    Listing l = seedListing();
    User consumer = new User();
    consumer.setEmail("c-" + UUID.randomUUID() + "@x");
    consumer.setRole(UserRole.consumer);
    consumer = users.save(consumer);
    Booking b = new Booking();
    b.setListing(l);
    b.setProvider(providers.findById(providerPrincipal.id()).orElseThrow());
    b.setConsumer(consumer);
    b.setStatus(BookingStatus.pending);
    b.setAmountPaidCents(8000);
    b.setPlatformFeeCents(1200);
    b.setProviderPayoutCents(6800);
    b.setCurrency("USD");
    b.setPendingExpiresAt(Instant.now().plusSeconds(2100));
    b.setRedemptionCode("ABCD2345");
    bookings.save(b);

    mvc.perform(
            patch("/api/providers/me/settings/currency")
                .with(asProvider())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currency", "EUR"))))
        .andExpect(status().isConflict());
  }

  @Test
  void self_correct_allowed_when_only_listings_exist_no_bookings() throws Exception {
    // M2 blocked here; M3 (which tightened predicate to bookings) now allows.
    seedListing();
    mvc.perform(
            patch("/api/providers/me/settings/currency")
                .with(asProvider())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currency", "EUR"))))
        .andExpect(status().isNoContent());
  }

  @Test
  void admin_can_override_currency_after_listings_exist_and_writes_audit() throws Exception {
    seedListing();

    mvc.perform(
            post("/api/admin/providers/" + providerPrincipal.id() + "/currency")
                .with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("currency", "EUR", "reason", "merchant requested in support ticket #4128"))))
        .andExpect(status().isOk());

    assertThat(providers.findById(providerPrincipal.id()).orElseThrow().getCurrency())
        .isEqualTo("EUR");
    assertThat(audit.count()).isEqualTo(1);
    var entry = audit.findAll().get(0);
    assertThat(entry.getAction()).isEqualTo("provider.change_currency");
    assertThat(entry.getReason()).contains("support ticket");
    // jsonb reformats keys (alphabetizes, adds spaces); assert content not format.
    assertThat(entry.getPayload()).contains("USD").contains("EUR");
  }

  @Test
  void admin_override_rejects_short_reason() throws Exception {
    mvc.perform(
            post("/api/admin/providers/" + providerPrincipal.id() + "/currency")
                .with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currency", "EUR", "reason", "short"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void self_correct_requires_provider_role() throws Exception {
    mvc.perform(
            patch("/api/providers/me/settings/currency")
                .with(user("c").roles("CONSUMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currency", "EUR"))))
        .andExpect(status().isForbidden());
  }
}
