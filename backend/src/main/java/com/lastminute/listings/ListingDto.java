package com.lastminute.listings;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ListingDto(
    UUID id,
    String title,
    String description,
    String city,
    String address,
    Double lat,
    Double lon,
    int originalPriceCents,
    int discountedPriceCents,
    String currency,
    Instant startTime,
    Instant endTime,
    String timezone,
    List<String> images,
    String categorySlug,
    String categoryName,
    String providerName) {

  public static ListingDto from(Listing l) {
    return new ListingDto(
        l.getId(),
        l.getTitle(),
        l.getDescription(),
        l.getCity(),
        l.getAddress(),
        l.getLat(),
        l.getLon(),
        l.getOriginalPriceCents(),
        l.getDiscountedPriceCents(),
        l.getCurrency(),
        l.getStartTime(),
        l.getEndTime(),
        l.getTimezone(),
        l.getImages(),
        l.getCategory().getSlug(),
        l.getCategory().getName(),
        l.getProvider().getBusinessName());
  }
}
