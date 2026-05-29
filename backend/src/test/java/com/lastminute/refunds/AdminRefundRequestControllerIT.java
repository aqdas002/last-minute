package com.lastminute.refunds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AdminRefundRequestControllerIT extends IntegrationTestBase {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired BookingRepository bookings;
  @Autowired UserRepository users;
  @Autowired ProviderRepository providers;
  @Autowired CategoryRepository categories;
  @Autowired ListingRepository listings;
  @Autowired RefundRequestRepository requests;

  RefundRequest seedOpenRequest(String listingTitle, RefundReason reason) {
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
    l.setTitle(listingTitle);
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
    b.setStripePaymentIntentId("pi_" + UUID.randomUUID());
    b.setConfirmedAt(Instant.now());
    b = bookings.save(b);

    RefundRequest r = new RefundRequest();
    r.setBooking(b);
    r.setConsumer(consumer);
    r.setReason(reason);
    r.setDetails("seeded");
    r.setStatus(RefundRequestStatus.open);
    return requests.saveAndFlush(r);
  }

  @Test
  void non_admin_cannot_list_or_act() throws Exception {
    mvc.perform(get("/api/admin/refund-requests").with(user("u").roles("CONSUMER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void admin_list_default_returns_only_open() throws Exception {
    seedOpenRequest("Yoga", RefundReason.provider_no_show);
    RefundRequest closed = seedOpenRequest("Past", RefundReason.quality_issue);
    closed.setStatus(RefundRequestStatus.denied);
    requests.saveAndFlush(closed);

    mvc.perform(get("/api/admin/refund-requests").with(user("admin").roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].listingTitle").value("Yoga"));
  }

  @Test
  void admin_list_with_status_filter_returns_matching() throws Exception {
    RefundRequest denied = seedOpenRequest("Denied case", RefundReason.duplicate_charge);
    denied.setStatus(RefundRequestStatus.denied);
    requests.saveAndFlush(denied);

    mvc.perform(
            get("/api/admin/refund-requests")
                .param("status", "denied")
                .with(user("admin").roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].status").value("denied"));
  }

  @Test
  void admin_can_deny_open_request_with_notes() throws Exception {
    RefundRequest r = seedOpenRequest("To deny", RefundReason.other);

    mvc.perform(
            post("/api/admin/refund-requests/{id}/deny", r.getId())
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("notes", "Could not verify claim."))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("denied"))
        .andExpect(jsonPath("$.adminNotes").value("Could not verify claim."))
        .andExpect(jsonPath("$.resolvedAt").exists());

    RefundRequest after = requests.findById(r.getId()).orElseThrow();
    assertThat(after.getStatus()).isEqualTo(RefundRequestStatus.denied);
  }

  @Test
  void admin_deny_already_resolved_returns_409() throws Exception {
    RefundRequest r = seedOpenRequest("Already denied", RefundReason.other);
    r.setStatus(RefundRequestStatus.auto_resolved);
    requests.saveAndFlush(r);

    mvc.perform(
            post("/api/admin/refund-requests/{id}/deny", r.getId())
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("notes", "second deny"))))
        .andExpect(status().isConflict());
  }

  @Test
  void admin_can_attach_notes_without_resolving() throws Exception {
    RefundRequest r = seedOpenRequest("Note me", RefundReason.provider_no_show);

    mvc.perform(
            post("/api/admin/refund-requests/{id}/notes", r.getId())
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("notes", "Followed up with provider"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("open"))
        .andExpect(jsonPath("$.adminNotes").value("Followed up with provider"));
  }
}
