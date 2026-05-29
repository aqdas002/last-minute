package com.lastminute.providers;

import com.lastminute.auth.MagicLinkService;
import com.lastminute.users.User;
import com.lastminute.users.UserRepository;
import com.lastminute.users.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Spec §5 Flow 2 step 1–2: create a provider account and send the first magic link. KYC happens
 * in Stripe-hosted onboarding (next step, {@link OnboardingController}).
 */
@Service
public class ProviderService {

  private final UserRepository users;
  private final ProviderRepository providers;
  private final MagicLinkService magicLink;

  public ProviderService(
      UserRepository users, ProviderRepository providers, MagicLinkService magicLink) {
    this.users = users;
    this.providers = providers;
    this.magicLink = magicLink;
  }

  @Transactional
  public void signup(SignupRequest req) {
    if (users.findByEmail(req.email()).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "email_already_registered");
    }

    User u = new User();
    u.setEmail(req.email());
    u.setRole(UserRole.provider);
    u = users.save(u);

    Provider p = new Provider();
    p.setId(u.getId());
    p.setBusinessName(req.businessName());
    p.setCurrency(req.currency());
    p.setTimezone(req.timezone());
    p.setStatus(ProviderStatus.pending_kyc);
    providers.save(p);

    // Magic link drops them into the onboarding flow after they click it.
    magicLink.request(req.email(), "/provider/onboarding");
  }

  public record SignupRequest(String email, String businessName, String currency, String timezone) {}
}
