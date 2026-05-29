package com.lastminute.bookings;

import com.lastminute.auth.ResendClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spec §5 Flow 1 step 9 — T-1h booking reminder. Runs every 5 minutes; sends one email per
 * confirmed booking whose {@code listing.start_time} falls in [now, now+65min] and that hasn't been
 * reminded yet. The 5-minute window overlap (60 vs 65) absorbs jitter without double-sending: we
 * stamp {@code reminder_sent_at} per booking after sending.
 */
@Component
public class ReminderEmailJob {

  private static final Logger LOG = LoggerFactory.getLogger(ReminderEmailJob.class);
  private static final DateTimeFormatter TS_FMT =
      DateTimeFormatter.ofPattern("h:mm a 'on' EEE, MMM d");

  private final BookingRepository bookings;
  private final ResendClient email;
  private final Clock clock;

  public ReminderEmailJob(BookingRepository bookings, ResendClient email, Clock clock) {
    this.bookings = bookings;
    this.email = email;
    this.clock = clock;
  }

  @Scheduled(cron = "0 */5 * * * *")
  @Transactional
  public void run() {
    Instant now = Instant.now(clock);
    Instant horizon = now.plus(Duration.ofMinutes(65));

    var due = bookings.findRemindersDue(now, horizon, BookingStatus.confirmed);
    if (due.isEmpty()) return;

    for (Booking b : due) {
      try {
        var listing = b.getListing();
        String ts =
            TS_FMT.format(listing.getStartTime().atZone(ZoneId.of(listing.getTimezone())));
        email.sendBookingReminder(
            b.getConsumer().getEmail(), listing.getTitle(), ts, b.getRedemptionCode());
        b.setReminderSentAt(now);
        bookings.save(b);
      } catch (Exception e) {
        LOG.error("reminder send failed for booking {}", b.getId(), e);
        // Don't stamp reminder_sent_at — the next tick will retry.
      }
    }
    LOG.info("sent {} reminder(s)", due.size());
  }
}
