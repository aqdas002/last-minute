package com.lastminute.bookings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.lastminute.auth.ResendClient;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import(ReminderEmailJobIT.FixedClock.class)
class ReminderEmailJobIT extends IntegrationTestBase {

  static final Instant T0 = Instant.parse("2026-06-01T12:00:00Z");

  @TestConfiguration
  static class FixedClock {
    @Bean
    @Primary
    Clock testClock() {
      return Clock.fixed(T0, ZoneOffset.UTC);
    }
  }

  @MockBean ResendClient email;

  @Autowired ReminderEmailJob job;
  @Autowired BookingRepository bookings;
  @Autowired UserRepository users;
  @Autowired ProviderRepository providers;
  @Autowired CategoryRepository categories;
  @Autowired ListingRepository listings;

  Booking seed(Instant startTime, BookingStatus status, Instant reminderSentAt) {
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
    l.setTitle("Sunset Yoga");
    l.setOriginalPriceCents(10000);
    l.setDiscountedPriceCents(8000);
    l.setCurrency("USD");
    l.setCapacity(1);
    l.setStartTime(startTime);
    l.setEndTime(startTime.plus(1, ChronoUnit.HOURS));
    l.setListingExpiresAt(startTime.minus(10, ChronoUnit.MINUTES));
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
    b.setPendingExpiresAt(startTime.plusSeconds(3600));
    b.setRedemptionCode("ABCD" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 4).toUpperCase());
    if (status == BookingStatus.confirmed) b.setConfirmedAt(T0.minus(2, ChronoUnit.HOURS));
    if (reminderSentAt != null) b.setReminderSentAt(reminderSentAt);
    return bookings.save(b);
  }

  @Test
  void sends_reminder_for_confirmed_booking_starting_in_45_minutes() {
    Booking b =
        seed(T0.plus(45, ChronoUnit.MINUTES), BookingStatus.confirmed, null);

    job.run();

    verify(email, times(1))
        .sendBookingReminder(
            eq(b.getConsumer().getEmail()),
            eq("Sunset Yoga"),
            any(String.class),
            eq(b.getRedemptionCode()));

    Booking after = bookings.findById(b.getId()).orElseThrow();
    assertThat(after.getReminderSentAt()).isNotNull();
  }

  @Test
  void does_not_resend_when_reminder_sent_at_already_set() {
    seed(T0.plus(45, ChronoUnit.MINUTES), BookingStatus.confirmed, T0.minus(10, ChronoUnit.MINUTES));

    job.run();

    verify(email, never())
        .sendBookingReminder(any(), any(), any(), any());
  }

  @Test
  void does_not_remind_for_pending_or_cancelled_bookings() {
    seed(T0.plus(45, ChronoUnit.MINUTES), BookingStatus.pending, null);
    seed(T0.plus(45, ChronoUnit.MINUTES), BookingStatus.cancelled, null);

    job.run();

    verify(email, never())
        .sendBookingReminder(any(), any(), any(), any());
  }

  @Test
  void does_not_remind_for_booking_starting_2_hours_out() {
    seed(T0.plus(2, ChronoUnit.HOURS), BookingStatus.confirmed, null);

    job.run();

    verify(email, never())
        .sendBookingReminder(any(), any(), any(), any());
  }
}
