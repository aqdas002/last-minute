package com.lastminute.stripe;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Initializes the Stripe SDK's static {@code apiKey} from {@code app.stripe.secret-key}.
 * The SDK reads this static field on every call, so set-once at boot is sufficient.
 *
 * <p>When the env var is empty (dev/test), Stripe calls will throw at runtime. That's the right
 * behavior — tests stub the Stripe surface via WireMock so they don't hit the real SDK; production
 * deploys must set the env var.
 */
@Configuration
public class StripeConfig {

  private static final Logger LOG = LoggerFactory.getLogger(StripeConfig.class);

  @Value("${app.stripe.secret-key:}")
  private String secretKey;

  @PostConstruct
  void init() {
    if (secretKey == null || secretKey.isBlank()) {
      LOG.warn(
          "STRIPE_SECRET_KEY is empty — Stripe SDK calls will throw. OK for tests (WireMock stubs)"
              + " and dev profiles that don't exercise Stripe.");
      return;
    }
    Stripe.apiKey = secretKey;
    LOG.info("Stripe SDK initialized");
  }
}
