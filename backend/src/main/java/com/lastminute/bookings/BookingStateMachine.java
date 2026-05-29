package com.lastminute.bookings;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spec §3.4: enforces the booking-status transitions in code.
 *
 * <pre>
 *   pending   → confirmed | cancelled
 *   confirmed → cancelled | completed | no_show
 *   completed → cancelled (refund / chargeback)
 *   no_show   → cancelled (auto-refund pipeline)
 *   cancelled is terminal
 * </pre>
 *
 * <p>Idempotent: a transition whose target equals the current status returns
 * {@code alreadyApplied=true} instead of throwing.
 */
@Service
public class BookingStateMachine {

  @PersistenceContext private EntityManager em;
  private final BookingRepository bookings;
  private final Clock clock;

  public BookingStateMachine(BookingRepository bookings, Clock clock) {
    this.bookings = bookings;
    this.clock = clock;
  }

  /** Result of a transition. */
  public record Result(boolean applied, boolean alreadyApplied) {
    public static Result ofApplied() {
      return new Result(true, false);
    }
    public static Result ofAlready() {
      return new Result(false, true);
    }
  }

  @Transactional
  public Result transition(UUID bookingId, BookingStatus from, BookingStatus to, CancellationReason reason) {
    if (!isAllowed(from, to)) {
      throw new IllegalArgumentException(
          "illegal transition " + from + " → " + to + " (not in spec §3.4)");
    }

    Instant now = Instant.now(clock);

    // Build the field updates based on the target state.
    StringBuilder sql = new StringBuilder(
        "UPDATE bookings SET status = CAST(:to AS booking_status), updated_at = :now");
    if (to == BookingStatus.confirmed) sql.append(", confirmed_at = :now");
    if (to == BookingStatus.cancelled) sql.append(", cancelled_at = :now");
    if (reason != null) sql.append(", cancellation_reason = CAST(:reason AS cancellation_reason)");
    sql.append(" WHERE id = :id AND status = CAST(:from AS booking_status)");

    var q = em.createNativeQuery(sql.toString())
        .setParameter("to", to.name())
        .setParameter("from", from.name())
        .setParameter("id", bookingId)
        .setParameter("now", now);
    if (reason != null) q.setParameter("reason", reason.name());

    int affected = q.executeUpdate();
    if (affected == 1) return Result.ofApplied();

    // Zero rows: either the booking doesn't exist, or it's not in `from` state. Distinguish.
    Booking current =
        bookings
            .findById(bookingId)
            .orElseThrow(() -> new IllegalArgumentException("booking_not_found"));
    if (current.getStatus() == to) {
      return Result.ofAlready();
    }
    throw new IllegalStateException(
        "booking " + bookingId + " is in " + current.getStatus() + ", cannot " + from + "→" + to);
  }

  private static final Set<String> ALLOWED =
      Set.of(
          "pending→confirmed",
          "pending→cancelled",
          "confirmed→cancelled",
          "confirmed→completed",
          "confirmed→no_show",
          "completed→cancelled",
          "no_show→cancelled");

  static boolean isAllowed(BookingStatus from, BookingStatus to) {
    return ALLOWED.contains(from.name() + "→" + to.name());
  }
}
