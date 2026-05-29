package com.lastminute.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lastminute.auth.CurrentUser;
import com.lastminute.stripe.StripeService;
import com.lastminute.support.IntegrationTestBase;
import com.lastminute.users.User;
import com.lastminute.users.UserRepository;
import com.lastminute.users.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
class OnboardingControllerIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private UserRepository users;
  @Autowired private ProviderRepository providers;

  @MockitoBean private StripeService stripe;

  private CurrentUser principal;

  @BeforeEach
  @Transactional
  void seedProvider() {
    User u = new User();
    u.setEmail("studio@example.com");
    u.setRole(UserRole.provider);
    u = users.save(u);

    Provider p = new Provider();
    p.setId(u.getId());
    p.setBusinessName("Sunset Yoga Studio");
    p.setCurrency("USD");
    p.setTimezone("America/New_York");
    p.setCountry("US");
    p.setStatus(ProviderStatus.pending_kyc);
    providers.save(p);

    principal = new CurrentUser(u.getId(), u.getEmail(), UserRole.provider);
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor asProvider() {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_PROVIDER")));
    return authentication(auth);
  }

  @Test
  void link_creates_stripe_account_when_none_exists_and_returns_url() throws Exception {
    when(stripe.createConnectedAccount(eq("studio@example.com"), any()))
        .thenReturn("acct_TEST123");
    when(stripe.createAccountOnboardingLink(eq("acct_TEST123"), any(), any()))
        .thenReturn("https://connect.stripe.com/setup/e/acct_TEST123/abc");

    mvc.perform(post("/api/providers/onboarding/link").with(asProvider()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("https://connect.stripe.com/setup/e/acct_TEST123/abc"));

    Provider after = providers.findById(principal.id()).orElseThrow();
    assertThat(after.getStripeAccountId()).isEqualTo("acct_TEST123");

    verify(stripe, times(1)).createConnectedAccount(any(), any());
  }

  @Test
  void link_reuses_existing_stripe_account() throws Exception {
    Provider p = providers.findById(principal.id()).orElseThrow();
    p.setStripeAccountId("acct_REUSE");
    providers.save(p);

    when(stripe.createAccountOnboardingLink(eq("acct_REUSE"), any(), any()))
        .thenReturn("https://connect.stripe.com/setup/e/acct_REUSE/xyz");

    mvc.perform(post("/api/providers/onboarding/link").with(asProvider()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("https://connect.stripe.com/setup/e/acct_REUSE/xyz"));

    // critical: did NOT create a new connected account
    verify(stripe, times(0)).createConnectedAccount(any(), any());
  }

  @Test
  void state_returns_persisted_flags() throws Exception {
    Provider p = providers.findById(principal.id()).orElseThrow();
    p.setStripeAccountId("acct_STATE");
    p.setStripeChargesEnabled(true);
    p.setStripePayoutsEnabled(false);
    providers.save(p);

    mvc.perform(get("/api/providers/onboarding/state").with(asProvider()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stripeAccountId").value("acct_STATE"))
        .andExpect(jsonPath("$.chargesEnabled").value(true))
        .andExpect(jsonPath("$.payoutsEnabled").value(false))
        .andExpect(jsonPath("$.status").value("pending_kyc"));
  }

  @Test
  void onboarding_link_requires_provider_role() throws Exception {
    mvc.perform(post("/api/providers/onboarding/link").with(user("consumer").roles("CONSUMER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void onboarding_state_requires_authenticated_user() throws Exception {
    // Spring Security returns 403 (not 401) for both "no auth" and "wrong role"
    // when the rule is hasAuthority(...). M3 can revisit if we want stricter 401-vs-403 semantics.
    mvc.perform(get("/api/providers/onboarding/state"))
        .andExpect(status().is4xxClientError());
  }
}
