package com.lastminute.providers;

import com.lastminute.auth.CurrentUser;
import com.lastminute.stripe.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spec §5 Flow 2 steps 3–6: provider clicks through to Stripe-hosted KYC and Stripe redirects
 * them back. The actual status flip lives in {@link AccountUpdatedHandler} (Task 7) — it listens
 * to the {@code account.updated} webhook.
 */
@RestController
@RequestMapping("/api/providers/onboarding")
public class OnboardingController {

  private static final Logger LOG = LoggerFactory.getLogger(OnboardingController.class);

  private final ProviderRepository providers;
  private final StripeService stripe;
  private final String frontendOrigin;

  public OnboardingController(
      ProviderRepository providers,
      StripeService stripe,
      @Value("${app.frontend-origin:http://localhost:5173}") String frontendOrigin) {
    this.providers = providers;
    this.stripe = stripe;
    this.frontendOrigin = frontendOrigin;
  }

  /**
   * Generate (or reuse) a Stripe Connect Express account and return a one-time onboarding URL.
   * Frontend redirects the browser to this URL.
   */
  @PostMapping("/link")
  @Transactional
  public ResponseEntity<LinkResponse> createLink(
      @AuthenticationPrincipal CurrentUser principal, HttpServletRequest req)
      throws StripeException {
    Provider provider =
        providers
            .findById(principal.id())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "no provider row for user " + principal.id()));

    if (provider.getStripeAccountId() == null) {
      String accountId = stripe.createConnectedAccount(principal.email(), provider.getCountry());
      provider.setStripeAccountId(accountId);
      providers.save(provider);
      LOG.info("created Stripe Connect account {} for provider {}", accountId, principal.id());
    }

    String url =
        stripe.createAccountOnboardingLink(
            provider.getStripeAccountId(),
            frontendOrigin + "/provider/onboarding/return",
            frontendOrigin + "/provider/onboarding");
    return ResponseEntity.ok(new LinkResponse(url));
  }

  /**
   * Poll-friendly view of the provider's onboarding state. Frontend polls this after Stripe
   * redirects back to the return URL.
   */
  @GetMapping("/state")
  public ResponseEntity<StateResponse> state(@AuthenticationPrincipal CurrentUser principal) {
    Provider provider =
        providers
            .findById(principal.id())
            .orElseThrow(() -> new IllegalStateException("no provider row"));
    return ResponseEntity.ok(
        new StateResponse(
            provider.getStripeAccountId(),
            provider.isStripeChargesEnabled(),
            provider.isStripePayoutsEnabled(),
            provider.getStatus().name()));
  }

  /**
   * Stripe Connect Express dashboard link — providers click through to see balances, payouts,
   * and tax docs in Stripe's hosted UI. Single-use URL so we mint a fresh one per click.
   */
  @PostMapping("/dashboard-link")
  public ResponseEntity<LinkResponse> dashboardLink(@AuthenticationPrincipal CurrentUser principal)
      throws StripeException {
    Provider provider =
        providers
            .findById(principal.id())
            .orElseThrow(() -> new IllegalStateException("no provider row"));
    if (provider.getStripeAccountId() == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "NOT_ONBOARDED");
    }
    if (!provider.isStripeChargesEnabled()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "ONBOARDING_INCOMPLETE");
    }
    return ResponseEntity.ok(
        new LinkResponse(stripe.createExpressDashboardLink(provider.getStripeAccountId())));
  }

  public record LinkResponse(String url) {}

  public record StateResponse(
      String stripeAccountId, boolean chargesEnabled, boolean payoutsEnabled, String status) {}
}
