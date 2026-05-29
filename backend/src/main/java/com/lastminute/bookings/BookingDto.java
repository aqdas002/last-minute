package com.lastminute.bookings;

import java.time.Instant;
import java.util.UUID;

public record BookingDto(
    UUID id,
    UUID listingId,
    String listingTitle,
    BookingStatus status,
    int amountPaidCents,
    String currency,
    Instant startTime,
    Instant pendingExpiresAt,
    Instant confirmedAt,
    String redemptionCode,
    String checkoutUrlIfAny) {

  public static BookingDto of(Booking b, String checkoutUrl) {
    return new BookingDto(
        b.getId(),
        b.getListing().getId(),
        b.getListing().getTitle(),
        b.getStatus(),
        b.getAmountPaidCents(),
        b.getCurrency(),
        b.getListing().getStartTime(),
        b.getPendingExpiresAt(),
        b.getConfirmedAt(),
        b.getStatus() == BookingStatus.confirmed ? b.getRedemptionCode() : null,
        checkoutUrl);
  }
}
