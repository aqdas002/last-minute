package com.lastminute.bookings;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Spec §5 Flow 4 + §6.1: minute-cron that cancels {@code pending} bookings past their TTL,
 * releasing inventory. The {@code pending_expires_at = now + 35 min} window is set by
 * {@link ReserveSpotService}; the sweeper closes the loop when Stripe Checkout was abandoned.
 */
@Component
public class PendingBookingSweeper {

  private static final Logger LOG = LoggerFactory.getLogger(PendingBookingSweeper.class);

  private final BookingRepository bookings;
  private final BookingStateMachine stateMachine;
  private final Clock clock;

  public PendingBookingSweeper(
      BookingRepository bookings, BookingStateMachine stateMachine, Clock clock) {
    this.bookings = bookings;
    this.stateMachine = stateMachine;
    this.clock = clock;
  }

  /** Every minute. Use {@link #sweepNow()} from tests with a frozen clock. */
  @Scheduled(cron = "0 * * * * *")
  public void sweep() {
    sweepNow();
  }

  /** Synchronous variant for tests. Returns the number of bookings cancelled. */
  public int sweepNow() {
    Instant now = Instant.now(clock);
    var expired = bookings.findExpiredPending(now);
    int cancelled = 0;
    for (Booking b : expired) {
      try {
        stateMachine.transition(
            b.getId(), BookingStatus.pending, BookingStatus.cancelled, CancellationReason.consumer_no_pay);
        cancelled++;
      } catch (Exception e) {
        LOG.warn("could not cancel expired pending {}: {}", b.getId(), e.getMessage());
      }
    }
    if (cancelled > 0) LOG.info("sweeper cancelled {} expired pending bookings", cancelled);
    return cancelled;
  }
}
