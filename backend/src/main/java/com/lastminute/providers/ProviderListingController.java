package com.lastminute.providers;

import com.lastminute.auth.CurrentUser;
import com.lastminute.listings.Listing;
import com.lastminute.listings.ListingDto;
import com.lastminute.pricing.PricingService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/providers/me/listings")
public class ProviderListingController {

  private final ProviderListingService svc;
  private final PricingService pricing;

  public ProviderListingController(ProviderListingService svc, PricingService pricing) {
    this.svc = svc;
    this.pricing = pricing;
  }

  @GetMapping
  public List<ListingDto> mine(@AuthenticationPrincipal CurrentUser principal) {
    return svc.mine(principal.id()).stream().map(ListingDto::from).toList();
  }

  @PostMapping
  public ResponseEntity<ListingDto> create(
      @AuthenticationPrincipal CurrentUser principal, @Valid @RequestBody CreateBody body) {
    Listing l =
        svc.create(
            principal.id(),
            new ProviderListingService.CreateRequest(
                body.categoryId(),
                body.title(),
                body.description(),
                body.originalPriceCents(),
                body.discountedPriceCents(),
                body.capacity(),
                body.startTime(),
                body.endTime(),
                body.listingExpiresAt()));
    return ResponseEntity.ok(ListingDto.from(l));
  }

  @PatchMapping("/{id}")
  public ResponseEntity<ListingDto> edit(
      @AuthenticationPrincipal CurrentUser principal,
      @PathVariable UUID id,
      @RequestBody EditBody body) {
    Listing l =
        svc.edit(
            principal.id(),
            id,
            new ProviderListingService.EditRequest(
                body.title(),
                body.description(),
                body.images(),
                body.originalPriceCents(),
                body.discountedPriceCents(),
                body.capacity(),
                body.startTime(),
                body.endTime(),
                body.listingExpiresAt()));
    return ResponseEntity.ok(ListingDto.from(l));
  }

  @PostMapping("/{id}/publish")
  public ResponseEntity<ListingDto> publish(
      @AuthenticationPrincipal CurrentUser principal, @PathVariable UUID id) {
    return ResponseEntity.ok(ListingDto.from(svc.publish(principal.id(), id)));
  }

  @PostMapping("/{id}/suspend")
  public ResponseEntity<ListingDto> suspend(
      @AuthenticationPrincipal CurrentUser principal, @PathVariable UUID id) {
    return ResponseEntity.ok(ListingDto.from(svc.suspend(principal.id(), id)));
  }

  @PostMapping("/{id}/unsuspend")
  public ResponseEntity<ListingDto> unsuspend(
      @AuthenticationPrincipal CurrentUser principal, @PathVariable UUID id) {
    return ResponseEntity.ok(ListingDto.from(svc.unsuspend(principal.id(), id)));
  }

  /** Spec §5 Flow 2: live "you receive $X.XX after our 15%" math for the listing-create UI. */
  @GetMapping("/preview-fee")
  public ResponseEntity<FeePreview> previewFee(@RequestParam long priceCents) {
    long fee = pricing.platformFeeCents(priceCents);
    long payout = pricing.providerPayoutCents(priceCents);
    return ResponseEntity.ok(new FeePreview(priceCents, fee, payout));
  }

  public record CreateBody(
      UUID categoryId,
      String title,
      String description,
      int originalPriceCents,
      int discountedPriceCents,
      int capacity,
      Instant startTime,
      Instant endTime,
      Instant listingExpiresAt) {}

  public record EditBody(
      String title,
      String description,
      List<String> images,
      Integer originalPriceCents,
      Integer discountedPriceCents,
      Integer capacity,
      Instant startTime,
      Instant endTime,
      Instant listingExpiresAt) {}

  public record FeePreview(long priceCents, long platformFeeCents, long providerPayoutCents) {}
}
