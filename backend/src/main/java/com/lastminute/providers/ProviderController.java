package com.lastminute.providers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {

  private final ProviderService providerService;

  public ProviderController(ProviderService providerService) {
    this.providerService = providerService;
  }

  /** Anyone can request a provider account; KYC happens after they click the magic link. */
  @PostMapping("/signup")
  public ResponseEntity<Void> signup(@Valid @RequestBody SignupBody body) {
    providerService.signup(
        new ProviderService.SignupRequest(
            body.email(), body.businessName(), body.currency(), body.timezone()));
    return ResponseEntity.accepted().build();
  }

  public record SignupBody(
      @NotBlank @Email String email,
      @NotBlank String businessName,
      @NotBlank @Size(min = 3, max = 3) String currency,
      @NotBlank String timezone) {}
}
