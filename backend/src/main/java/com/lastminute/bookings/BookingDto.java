package com.lastminute.bookings;

import java.time.Instant;
import java.util.UUID;

public record BookingDto(
    UUID id,
    UUID listingId,
    String listingTitle,
    String listingAddress,
    String listingCity,
    Instant listingEndTime,
    String listingTimezone,
    String providerName,
    BookingStatus status,
    String cancellationReason,
    int amountPaidCents,
    String currency,
    Instant startTime,
    Instant pendingExpiresAt,
    Instant confirmedAt,
    Instant cancelledAt,
    Instant redeemedAt,
    String redemptionCode,
    String checkoutUrlIfAny) {

  public static BookingDto of(Booking b, String checkoutUrl) {
    return new BookingDto(
        b.getId(),
        b.getListing().getId(),
        b.getListing().getTitle(),
        b.getListing().getAddress(),
        b.getListing().getCity(),
        b.getListing().getEndTime(),
        b.getListing().getTimezone(),
        b.getProvider().getBusinessName(),
        b.getStatus(),
        b.getCancellationReason() == null ? null : b.getCancellationReason().name(),
        b.getAmountPaidCents(),
        b.getCurrency(),
        b.getListing().getStartTime(),
        b.getPendingExpiresAt(),
        b.getConfirmedAt(),
        b.getCancelledAt(),
        b.getRedeemedAt(),
        b.getStatus() == BookingStatus.confirmed || b.getStatus() == BookingStatus.completed
            ? b.getRedemptionCode()
            : null,
        checkoutUrl);
  }
}
