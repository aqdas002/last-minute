package com.lastminute.pricing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Spec §3.2: single source of truth for commission math.
 *
 * <p>{@code platform_fee_cents = Math.floorDiv(amountCents * commissionPercent, 100)}.
 * Integer-only math (no floats). Sub-cent residual goes to the platform (floor rounding).
 */
@Service
public class PricingService {

  private final int commissionPercent;

  public PricingService(@Value("${app.commission-percent:15}") int commissionPercent) {
    if (commissionPercent < 0 || commissionPercent >= 100) {
      throw new IllegalArgumentException(
          "commissionPercent must be in [0, 100); got " + commissionPercent);
    }
    this.commissionPercent = commissionPercent;
  }

  public long platformFeeCents(long amountCents) {
    if (amountCents < 0) {
      throw new IllegalArgumentException("amountCents must be non-negative; got " + amountCents);
    }
    return Math.floorDiv(amountCents * commissionPercent, 100L);
  }

  public long providerPayoutCents(long amountCents) {
    return amountCents - platformFeeCents(amountCents);
  }
}
