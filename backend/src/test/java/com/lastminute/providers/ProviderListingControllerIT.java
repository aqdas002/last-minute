package com.lastminute.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lastminute.auth.CurrentUser;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
class ProviderListingControllerIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private UserRepository users;
  @Autowired private ProviderRepository providers;
  @Autowired private CategoryRepository categories;
  @Autowired private ListingRepository listings;

  private CurrentUser principal;
  private UUID categoryId;

  @BeforeEach
  void seed() {
    User u = new User();
    u.setEmail("studio@example.com");
    u.setRole(UserRole.provider);
    u = users.save(u);

    Provider p = new Provider();
    p.setId(u.getId());
    p.setBusinessName("Sunset Yoga Studio");
    p.setCurrency("USD");
    p.setTimezone("America/New_York");
    p.setCountry("US");
    p.setCity("New York");
    p.setStatus(ProviderStatus.active);
    p.setStripeChargesEnabled(true);
    p.setStripePayoutsEnabled(true);
    providers.save(p);

    Category c = new Category();
    c.setSlug("yoga");
    c.setName("Yoga");
    categoryId = categories.save(c).getId();

    principal = new CurrentUser(u.getId(), u.getEmail(), UserRole.provider);
  }

  private RequestPostProcessor asProvider() {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_PROVIDER")));
    return authentication(auth);
  }

  private Map<String, Object> validCreateBody() {
    Instant start = Instant.now().plusSeconds(3600);
    return Map.of(
        "categoryId", categoryId.toString(),
        "title", "7pm vinyasa flow",
        "description", "warm vinyasa with live music",
        "originalPriceCents", 12000,
        "discountedPriceCents", 8000,
        "capacity", 8,
        "startTime", start.toString(),
        "endTime", start.plusSeconds(2700).toString(),
        "listingExpiresAt", start.minusSeconds(600).toString());
  }

  @Test
  void mine_is_empty_when_no_listings() throws Exception {
    mvc.perform(get("/api/providers/me/listings").with(asProvider()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void create_returns_draft_listing() throws Exception {
    String body = json.writeValueAsString(validCreateBody());

    mvc.perform(
            post("/api/providers/me/listings")
                .with(asProvider())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("7pm vinyasa flow"))
        .andExpect(jsonPath("$.discountedPriceCents").value(8000));

    assertThat(listings.count()).isEqualTo(1);
    assertThat(listings.findAll().get(0).getStatus()).isEqualTo(ListingStatus.draft);
  }

  @Test
  void create_rejects_discounted_at_or_above_original() throws Exception {
    Map<String, Object> body = new java.util.HashMap<>(validCreateBody());
    body.put("discountedPriceCents", 12000);

    mvc.perform(
            post("/api/providers/me/listings")
                .with(asProvider())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void create_rejects_discounted_below_50_cents() throws Exception {
    Map<String, Object> body = new java.util.HashMap<>(validCreateBody());
    body.put("originalPriceCents", 100);
    body.put("discountedPriceCents", 49);

    mvc.perform(
            post("/api/providers/me/listings")
                .with(asProvider())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void publish_flips_draft_to_active() throws Exception {
    // Create a draft first
    mvc.perform(
            post("/api/providers/me/listings")
                .with(asProvider())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(validCreateBody())))
        .andExpect(status().isOk());

    Listing draft = listings.findAll().get(0);

    mvc.perform(post("/api/providers/me/listings/" + draft.getId() + "/publish").with(asProvider()))
        .andExpect(status().isOk());

    assertThat(listings.findById(draft.getId()).orElseThrow().getStatus())
        .isEqualTo(ListingStatus.active);
  }

  @Test
  void publish_rejected_when_charges_disabled() throws Exception {
    // Flip provider so charges are disabled
    Provider p = providers.findById(principal.id()).orElseThrow();
    p.setStripeChargesEnabled(false);
    providers.save(p);

    mvc.perform(
            post("/api/providers/me/listings")
                .with(asProvider())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(validCreateBody())))
        .andExpect(status().isOk());
    UUID listingId = listings.findAll().get(0).getId();

    mvc.perform(post("/api/providers/me/listings/" + listingId + "/publish").with(asProvider()))
        .andExpect(status().isConflict());
  }

  @Test
  void edit_non_material_field_always_allowed() throws Exception {
    mvc.perform(
            post("/api/providers/me/listings")
                .with(asProvider())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(validCreateBody())))
        .andExpect(status().isOk());
    UUID listingId = listings.findAll().get(0).getId();

    mvc.perform(
            patch("/api/providers/me/listings/" + listingId)
                .with(asProvider())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("title", "renamed"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("renamed"));
  }

  @Test
  void edit_other_provider_listing_returns_404() throws Exception {
    mvc.perform(
            post("/api/providers/me/listings")
                .with(asProvider())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(validCreateBody())))
        .andExpect(status().isOk());
    UUID listingId = listings.findAll().get(0).getId();

    // Build a different provider principal — same auth setup but a different user id.
    User other = new User();
    other.setEmail("other@example.com");
    other.setRole(UserRole.provider);
    other = users.save(other);
    Provider op = new Provider();
    op.setId(other.getId());
    op.setBusinessName("Other Co");
    op.setCurrency("USD");
    op.setTimezone("UTC");
    op.setCity("New York");
    op.setStatus(ProviderStatus.active);
    op.setStripeChargesEnabled(true);
    providers.save(op);

    CurrentUser otherPrincipal = new CurrentUser(other.getId(), other.getEmail(), UserRole.provider);
    Authentication otherAuth =
        new UsernamePasswordAuthenticationToken(
            otherPrincipal, null, List.of(new SimpleGrantedAuthority("ROLE_PROVIDER")));

    mvc.perform(
            patch("/api/providers/me/listings/" + listingId)
                .with(authentication(otherAuth))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("title", "hijack"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void preview_fee_returns_15_percent_split() throws Exception {
    mvc.perform(
            get("/api/providers/me/listings/preview-fee")
                .param("priceCents", "8000")
                .with(asProvider()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.priceCents").value(8000))
        .andExpect(jsonPath("$.platformFeeCents").value(1200))
        .andExpect(jsonPath("$.providerPayoutCents").value(6800));
  }
}
