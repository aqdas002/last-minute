package com.lastminute.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingServiceTest {

  private final PricingService p = new PricingService(15);

  @ParameterizedTest
  @CsvSource({
    "1, 0",
    "7, 1",
    "100, 15",
    "333, 49",
    "999, 149",
    "99999999999, 14999999999",
  })
  void floorDiv_by_15(long amountCents, long expected) {
    assertThat(p.platformFeeCents(amountCents)).isEqualTo(expected);
  }

  @Test
  void zero_returns_zero() {
    assertThat(p.platformFeeCents(0)).isEqualTo(0);
  }

  @Test
  void negative_throws() {
    assertThatThrownBy(() -> p.platformFeeCents(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("non-negative");
  }

  @Test
  void provider_payout_is_amount_minus_fee() {
    assertThat(p.providerPayoutCents(100)).isEqualTo(85);
    assertThat(p.providerPayoutCents(999)).isEqualTo(850);
  }

  @Test
  void commission_outside_range_throws() {
    assertThatThrownBy(() -> new PricingService(100)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PricingService(-1)).isInstanceOf(IllegalArgumentException.class);
  }
}
