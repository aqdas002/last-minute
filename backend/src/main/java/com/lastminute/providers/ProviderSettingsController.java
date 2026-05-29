package com.lastminute.providers;

import com.lastminute.auth.CurrentUser;
import com.lastminute.bookings.BookingRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/providers/me/settings")
public class ProviderSettingsController {

  private final ProviderRepository providers;
  private final BookingRepository bookings;

  public ProviderSettingsController(ProviderRepository providers, BookingRepository bookings) {
    this.providers = providers;
    this.bookings = bookings;
  }

  /**
   * Spec §5 Flow 2 step 9: self-correct only IFF no bookings exist for this provider. Admin
   * override at POST /api/admin/providers/{id}/currency covers the after-the-fact case with a
   * required reason logged to admin_actions.
   */
  @PatchMapping("/currency")
  @Transactional
  public ResponseEntity<Void> changeCurrency(
      @AuthenticationPrincipal CurrentUser principal, @Valid @RequestBody ChangeCurrencyBody body) {
    UUID providerId = principal.id();

    if (bookings.existsByProvider_Id(providerId)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "bookings_exist_use_admin_override");
    }

    Provider p =
        providers
            .findById(providerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no_provider"));
    p.setCurrency(body.currency());
    providers.save(p);
    return ResponseEntity.noContent().build();
  }

  public record ChangeCurrencyBody(@NotBlank @Size(min = 3, max = 3) String currency) {}
}
