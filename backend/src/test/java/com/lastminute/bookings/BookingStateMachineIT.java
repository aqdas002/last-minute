package com.lastminute.bookings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BookingStateMachineIT extends IntegrationTestBase {

  @Autowired BookingStateMachine sm;
  @Autowired BookingRepository bookings;
  @Autowired ListingRepository listings;
  @Autowired ProviderRepository providers;
  @Autowired CategoryRepository categories;
  @Autowired UserRepository users;

  UUID makePendingBooking() {
    User pUser = new User();
    pUser.setEmail("p" + UUID.randomUUID() + "@x");
    pUser.setRole(UserRole.provider);
    pUser = users.save(pUser);

    Provider p = new Provider();
    p.setId(pUser.getId());
    p.setBusinessName("P");
    p.setCurrency("USD");
    p.setTimezone("UTC");
    p.setStatus(ProviderStatus.active);
    p.setStripeChargesEnabled(true);
    providers.save(p);

    Category c = new Category();
    c.setSlug("s" + UUID.randomUUID());
    c.setName("C");
    c = categories.save(c);

    Listing l = new Listing();
    l.setProvider(p);
    l.setCategory(c);
    l.setTitle("T");
    l.setOriginalPriceCents(10000);
    l.setDiscountedPriceCents(8000);
    l.setCurrency("USD");
    l.setCapacity(1);
    Instant start = Instant.now().plusSeconds(7200);
    l.setStartTime(start);
    l.setEndTime(start.plusSeconds(3600));
    l.setListingExpiresAt(start.minusSeconds(600));
    l.setTimezone("UTC");
    l.setStatus(ListingStatus.active);
    l.setImages(new ArrayList<>());
    l = listings.save(l);

    User consumer = new User();
    consumer.setEmail("c" + UUID.randomUUID() + "@x");
    consumer.setRole(UserRole.consumer);
    consumer = users.save(consumer);

    Booking b = new Booking();
    b.setListing(l);
    b.setProvider(p);
    b.setConsumer(consumer);
    b.setStatus(BookingStatus.pending);
    b.setAmountPaidCents(8000);
    b.setPlatformFeeCents(1200);
    b.setProviderPayoutCents(6800);
    b.setCurrency("USD");
    b.setPendingExpiresAt(Instant.now().plusSeconds(2100));
    b.setRedemptionCode("ABCD2345");
    return bookings.save(b).getId();
  }

  @Test
  void pending_to_confirmed_applies() {
    UUID id = makePendingBooking();
    var r = sm.transition(id, BookingStatus.pending, BookingStatus.confirmed, null);
    assertThat(r.applied()).isTrue();
    assertThat(bookings.findById(id).orElseThrow().getStatus()).isEqualTo(BookingStatus.confirmed);
    assertThat(bookings.findById(id).orElseThrow().getConfirmedAt()).isNotNull();
  }

  @Test
  void replay_pending_to_confirmed_is_already_applied_no_op() {
    UUID id = makePendingBooking();
    sm.transition(id, BookingStatus.pending, BookingStatus.confirmed, null);
    var r = sm.transition(id, BookingStatus.pending, BookingStatus.confirmed, null);
    assertThat(r.alreadyApplied()).isTrue();
  }

  @Test
  void illegal_transition_pending_to_completed_throws() {
    UUID id = makePendingBooking();
    assertThatThrownBy(
            () -> sm.transition(id, BookingStatus.pending, BookingStatus.completed, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void confirmed_to_cancelled_with_reason_works() {
    UUID id = makePendingBooking();
    sm.transition(id, BookingStatus.pending, BookingStatus.confirmed, null);
    var r =
        sm.transition(id, BookingStatus.confirmed, BookingStatus.cancelled, CancellationReason.refund);
    assertThat(r.applied()).isTrue();
    Booking b = bookings.findById(id).orElseThrow();
    assertThat(b.getStatus()).isEqualTo(BookingStatus.cancelled);
    assertThat(b.getCancellationReason()).isEqualTo(CancellationReason.refund);
    assertThat(b.getCancelledAt()).isNotNull();
  }

  @Test
  void all_spec_allowed_transitions_pass_isAllowed_check() {
    String[][] allowed = {
      {"pending", "confirmed"},
      {"pending", "cancelled"},
      {"confirmed", "cancelled"},
      {"confirmed", "completed"},
      {"confirmed", "no_show"},
      {"completed", "cancelled"},
      {"no_show", "cancelled"},
    };
    for (String[] pair : allowed) {
      assertThat(BookingStateMachine.isAllowed(BookingStatus.valueOf(pair[0]), BookingStatus.valueOf(pair[1])))
          .as(pair[0] + "→" + pair[1])
          .isTrue();
    }
  }

  @Test
  void terminal_states_cannot_re_transition() {
    UUID id = makePendingBooking();
    sm.transition(id, BookingStatus.pending, BookingStatus.cancelled, CancellationReason.system);
    // cancelled is terminal — no transitions allowed out of it
    assertThat(BookingStateMachine.isAllowed(BookingStatus.cancelled, BookingStatus.pending)).isFalse();
    assertThat(BookingStateMachine.isAllowed(BookingStatus.cancelled, BookingStatus.confirmed)).isFalse();
  }
}
