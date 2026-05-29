package com.lastminute.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.stereotype.Service;

/**
 * Wraps Stripe Connect Express onboarding + checkout session creation. Spec §5 Flow 1 + Flow 2.
 *
 * <p>{@code account.updated} webhooks are handled in {@code AccountUpdatedHandler};
 * {@code checkout.session.completed} in {@code BookingConfirmedHandler}.
 */
@Service
public class StripeService {

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

  public Account retrieveAccount(String accountId) throws StripeException {
    return Account.retrieve(accountId);
  }

  /**
   * Spec §5 Flow 1 step 4: create a Stripe Checkout Session for a pending booking. {@code
   * expires_at = now + 30 min} matches Stripe's documented minimum; our {@code
   * pending_expires_at} is 35 min so the session expires first. Idempotency key = booking id.
   */
  public CheckoutSession createCheckoutSessionForBooking(
      String bookingId,
      String connectedAccountId,
      long amountCents,
      long applicationFeeCents,
      String currency,
      String successUrl,
      String cancelUrl)
      throws StripeException {

    long expiresAt = (System.currentTimeMillis() / 1000L) + (30L * 60L);

    SessionCreateParams params =
        SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setExpiresAt(expiresAt)
            .setSuccessUrl(successUrl)
            .setCancelUrl(cancelUrl)
            .putMetadata("booking_id", bookingId)
            .setPaymentIntentData(
                SessionCreateParams.PaymentIntentData.builder()
                    .setApplicationFeeAmount(applicationFeeCents)
                    .setTransferData(
                        SessionCreateParams.PaymentIntentData.TransferData.builder()
                            .setDestination(connectedAccountId)
                            .build())
                    .build())
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setQuantity(1L)
                    .setPriceData(
                        SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency(currency.toLowerCase())
                            .setUnitAmount(amountCents)
                            .setProductData(
                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName("Last-minute booking " + bookingId)
                                    .build())
                            .build())
                    .build())
            .build();

    RequestOptions options = RequestOptions.builder().setIdempotencyKey(bookingId).build();
    Session session = Session.create(params, options);
    return new CheckoutSession(session.getId(), session.getUrl());
  }

  /** Cancel a Stripe Checkout Session we created but no longer want (§5 Flow 1 step 4b). */
  public void expireCheckoutSession(String sessionId) throws StripeException {
    Session.retrieve(sessionId).expire();
  }

  public record CheckoutSession(String id, String url) {}
}
