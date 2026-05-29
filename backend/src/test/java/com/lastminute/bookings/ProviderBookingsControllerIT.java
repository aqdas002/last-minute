package com.lastminute.bookings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class ProviderBookingsControllerIT extends IntegrationTestBase {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserRepository users;
  @Autowired ProviderRepository providers;
  @Autowired CategoryRepository categories;
  @Autowired ListingRepository listings;
  @Autowired BookingRepository bookings;

  CurrentUser providerPrincipal;
  Provider provider;

  @BeforeEach
  void seedProvider() {
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
    provider = providers.save(p);
    providerPrincipal = new CurrentUser(u.getId(), u.getEmail(), UserRole.provider);
  }

  RequestPostProcessor asProvider() {
    Authentication a =
        new UsernamePasswordAuthenticationToken(
            providerPrincipal, null, List.of(new SimpleGrantedAuthority("ROLE_PROVIDER")));
    return authentication(a);
  }

  Booking seedConfirmedBookingStartingTodayPlusHours(int hoursFromNow, String code) {
    Category c = new Category();
    c.setSlug("c-" + UUID.randomUUID());
    c.setName("Cat");
    c = categories.save(c);

    Listing l = new Listing();
    l.setProvider(provider);
    l.setCategory(c);
    l.setTitle("Tonight's deal");
    l.setOriginalPriceCents(10000);
    l.setDiscountedPriceCents(8000);
    l.setCurrency("USD");
    l.setCapacity(1);
    Instant start = Instant.now().plus(hoursFromNow, ChronoUnit.HOURS);
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
    b.setRedemptionCode(code);
    b.setConfirmedAt(Instant.now());
    return bookings.save(b);
  }

  @Test
  void mark_no_show_for_past_confirmed_booking_flips_to_no_show() throws Exception {
    Booking b = seedConfirmedBookingStartingTodayPlusHours(-1, "NSHOW123");

    mvc.perform(
            post("/api/providers/me/bookings/{id}/mark-no-show", b.getId()).with(asProvider()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("OK"));

    Booking after = bookings.findById(b.getId()).orElseThrow();
    assertThat(after.getStatus()).isEqualTo(BookingStatus.no_show);
  }

  @Test
  void mark_no_show_before_start_time_returns_409_NOT_YET_STARTED() throws Exception {
    Booking b = seedConfirmedBookingStartingTodayPlusHours(2, "FUTURE12");

    mvc.perform(
            post("/api/providers/me/bookings/{id}/mark-no-show", b.getId()).with(asProvider()))
        .andExpect(status().isConflict());
  }

  @Test
  void mark_no_show_twice_returns_ALREADY_MARKED() throws Exception {
    Booking b = seedConfirmedBookingStartingTodayPlusHours(-1, "TWICEMRK");

    mvc.perform(
            post("/api/providers/me/bookings/{id}/mark-no-show", b.getId()).with(asProvider()))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/providers/me/bookings/{id}/mark-no-show", b.getId()).with(asProvider()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("ALREADY_MARKED"));
  }

  @Test
  void mark_no_show_for_someone_elses_booking_returns_404() throws Exception {
    Booking b = seedConfirmedBookingStartingTodayPlusHours(-1, "OTHER234");

    // Build a different provider principal
    User otherUser = new User();
    otherUser.setEmail("other-" + UUID.randomUUID() + "@x");
    otherUser.setRole(UserRole.provider);
    otherUser = users.save(otherUser);
    Provider op = new Provider();
    op.setId(otherUser.getId());
    op.setBusinessName("Other");
    op.setCurrency("USD");
    op.setTimezone("UTC");
    op.setCity("New York");
    op.setStatus(ProviderStatus.active);
    op.setStripeChargesEnabled(true);
    providers.save(op);
    CurrentUser otherP = new CurrentUser(otherUser.getId(), otherUser.getEmail(), UserRole.provider);
    org.springframework.security.core.Authentication otherAuth =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            otherP, null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PROVIDER")));

    mvc.perform(
            post("/api/providers/me/bookings/{id}/mark-no-show", b.getId())
                .with(authentication(otherAuth)))
        .andExpect(status().isNotFound());
  }

  @Test
  void summary_aggregates_payout_for_earning_bookings_only() throws Exception {
    // 2 earning (one confirmed, one completed) + 1 cancelled — only earning counts
    Booking earning1 = seedConfirmedBookingStartingTodayPlusHours(2, "SUMA1234");
    Booking earning2 = seedConfirmedBookingStartingTodayPlusHours(3, "SUMB1234");
    earning2.setStatus(BookingStatus.completed);
    bookings.save(earning2);
    Booking cancelled = seedConfirmedBookingStartingTodayPlusHours(4, "SUMC1234");
    cancelled.setStatus(BookingStatus.cancelled);
    bookings.save(cancelled);

    mvc.perform(get("/api/providers/me/bookings/summary").with(asProvider()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payoutCents").value(earning1.getProviderPayoutCents() + earning2.getProviderPayoutCents()))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.bookingsCount").value(2))
        .andExpect(jsonPath("$.cancelledCount").value(1))
        .andExpect(jsonPath("$.windowDays").value(30));
  }

  @Test
  void today_returns_confirmed_bookings_starting_today() throws Exception {
    seedConfirmedBookingStartingTodayPlusHours(2, "TODAY123");

    mvc.perform(get("/api/providers/me/bookings/today").with(asProvider()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].redemptionCode").value("TODAY123"));
  }

  @Test
  void redeem_with_valid_code_marks_completed() throws Exception {
    Booking b = seedConfirmedBookingStartingTodayPlusHours(2, "ABCD2345");

    mvc.perform(
            post("/api/providers/me/bookings/redeem")
                .with(asProvider())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("code", "ABCD2345"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("OK"));

    Booking after = bookings.findById(b.getId()).orElseThrow();
    assertThat(after.getStatus()).isEqualTo(BookingStatus.completed);
    assertThat(after.getRedeemedAt()).isNotNull();
  }

  @Test
  void redeem_unknown_code_returns_404_CODE_NOT_VALID() throws Exception {
    mvc.perform(
            post("/api/providers/me/bookings/redeem")
                .with(asProvider())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("code", "WXYZ7777"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CODE_NOT_VALID"));
  }

  @Test
  void second_redeem_returns_409_ALREADY_REDEEMED_with_timestamp() throws Exception {
    seedConfirmedBookingStartingTodayPlusHours(2, "TWICE234");

    mvc.perform(
            post("/api/providers/me/bookings/redeem")
                .with(asProvider())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("code", "TWICE234"))))
        .andExpect(status().isOk());

    mvc.perform(
            post("/api/providers/me/bookings/redeem")
                .with(asProvider())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("code", "TWICE234"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_REDEEMED"))
        .andExpect(jsonPath("$.redeemedAt").exists());
  }
}
