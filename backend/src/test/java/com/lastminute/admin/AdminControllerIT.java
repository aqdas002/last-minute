package com.lastminute.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lastminute.categories.CategoryRepository;
import com.lastminute.listings.ListingRepository;
import com.lastminute.providers.ProviderRepository;
import com.lastminute.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class AdminControllerIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private CategoryRepository categories;
  @Autowired private ProviderRepository providers;
  @Autowired private ListingRepository listings;

  @Test
  void unauthenticated_request_to_admin_is_forbidden() throws Exception {
    mvc.perform(
            post("/api/admin/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("slug", "fitness", "name", "Fitness"))))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void non_admin_user_to_admin_is_forbidden() throws Exception {
    mvc.perform(
            post("/api/admin/categories")
                .with(user("u").roles("CONSUMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("slug", "fitness", "name", "Fitness"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void admin_can_create_category_then_provider_then_listing() throws Exception {
    // Category
    MvcResult catResp =
        mvc.perform(
                post("/api/admin/categories")
                    .with(user("admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("slug", "fitness", "name", "Fitness"))))
            .andExpect(status().isOk())
            .andReturn();
    String categoryId = catResp.getResponse().getContentAsString().replace("\"", "");
    assertThat(categories.findBySlug("fitness")).isPresent();

    // Provider
    MvcResult provResp =
        mvc.perform(
                post("/api/admin/providers")
                    .with(user("admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "email", "biz@example.com",
                                "businessName", "Acme Yoga",
                                "currency", "USD",
                                "timezone", "America/New_York"))))
            .andExpect(status().isOk())
            .andReturn();
    String providerId = provResp.getResponse().getContentAsString().replace("\"", "");
    assertThat(providers.count()).isEqualTo(1);

    // Listing
    MvcResult listResp =
        mvc.perform(
                post("/api/admin/listings")
                    .with(user("admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "providerId", providerId,
                                "categoryId", categoryId,
                                "title", "7am yoga (sunset)",
                                "originalPriceCents", 12000,
                                "discountedPriceCents", 8000,
                                "startHoursFromNow", 2.0))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("7am yoga (sunset)"))
            .andExpect(jsonPath("$.discountedPriceCents").value(8000))
            .andReturn();
    JsonNode body = json.readTree(listResp.getResponse().getContentAsString());
    assertThat(body.get("categorySlug").asText()).isEqualTo("fitness");
    assertThat(listings.count()).isEqualTo(1);

    // ... and it now appears in the consumer feed via /api/listings/{id}.
    String listingId = body.get("id").asText();
    mvc.perform(get("/api/listings/" + listingId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("7am yoga (sunset)"));
  }

  @Test
  void admin_listing_rejects_discounted_at_or_above_original() throws Exception {
    mvc.perform(
            post("/api/admin/listings")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "providerId", "00000000-0000-0000-0000-000000000000",
                            "categoryId", "00000000-0000-0000-0000-000000000000",
                            "title", "Bad",
                            "originalPriceCents", 100,
                            "discountedPriceCents", 100,
                            "startHoursFromNow", 1.0))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void admin_listing_rejects_discounted_below_50_cents() throws Exception {
    mvc.perform(
            post("/api/admin/listings")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "providerId", "00000000-0000-0000-0000-000000000000",
                            "categoryId", "00000000-0000-0000-0000-000000000000",
                            "title", "Bad",
                            "originalPriceCents", 100,
                            "discountedPriceCents", 49,
                            "startHoursFromNow", 1.0))))
        .andExpect(status().isBadRequest());
  }
}
