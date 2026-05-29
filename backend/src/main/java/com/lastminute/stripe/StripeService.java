package com.lastminute.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import org.springframework.stereotype.Service;

/**
 * Wraps Stripe Connect Express onboarding. Spec §5 Flow 2:
 *
 * <ul>
 *   <li>Create an Express connected account for a provider.
 *   <li>Generate an onboarding URL for the provider's KYC flow.
 * </ul>
 *
 * <p>{@code account.updated} webhooks are handled separately (Task 7 — {@code
 * AccountUpdatedHandler}).
 */
@Service
public class StripeService {

  /**
   * Create a Stripe Connect Express account for {@code providerEmail}. Returns the Stripe account
   * id (e.g. {@code acct_1AbC2dEf...}).
   */
  public String createConnectedAccount(String providerEmail, String country) throws StripeException {
    AccountCreateParams params =
        AccountCreateParams.builder()
            .setType(AccountCreateParams.Type.EXPRESS)
            .setEmail(providerEmail)
            .setCountry(country == null ? "US" : country)
            .setCapabilities(
                AccountCreateParams.Capabilities.builder()
                    .setCardPayments(
                        AccountCreateParams.Capabilities.CardPayments.builder()
                            .setRequested(true)
                            .build())
                    .setTransfers(
                        AccountCreateParams.Capabilities.Transfers.builder()
                            .setRequested(true)
                            .build())
                    .build())
            .build();
    return Account.create(params).getId();
  }

  /**
   * Generate an onboarding link the provider opens in their browser. {@code returnUrl} is where
   * Stripe sends them after they finish; {@code refreshUrl} is where Stripe sends them if the link
   * has expired.
   */
  public String createAccountOnboardingLink(String accountId, String returnUrl, String refreshUrl)
      throws StripeException {
    AccountLinkCreateParams params =
        AccountLinkCreateParams.builder()
            .setAccount(accountId)
            .setReturnUrl(returnUrl)
            .setRefreshUrl(refreshUrl)
            .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
            .build();
    return AccountLink.create(params).getUrl();
  }

  /** Re-read an account from Stripe (used by onboarding-state polling). */
  public Account retrieveAccount(String accountId) throws StripeException {
    return Account.retrieve(accountId);
  }
}
