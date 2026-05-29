package com.lastminute.admin;

import com.lastminute.auth.CurrentUser;
import com.lastminute.providers.Provider;
import com.lastminute.providers.ProviderRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Admin-only endpoints scoped to a specific provider. Spec §5 Flow 2 step 9: admin can override
 * the currency lock after bookings have settled — every override writes to {@code admin_actions}
 * for traceability.
 */
@RestController
@RequestMapping("/api/admin/providers")
public class AdminProviderController {

  private final ProviderRepository providers;
  private final AdminActionRepository auditLog;
  private final ObjectMapper json;

  public AdminProviderController(
      ProviderRepository providers, AdminActionRepository auditLog, ObjectMapper json) {
    this.providers = providers;
    this.auditLog = auditLog;
    this.json = json;
  }

  @PostMapping("/{providerId}/currency")
  @Transactional
  public ResponseEntity<Map<String, UUID>> changeCurrency(
      @AuthenticationPrincipal CurrentUser admin,
      @PathVariable UUID providerId,
      @Valid @RequestBody OverrideBody body) {
    Provider p =
        providers
            .findById(providerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no_provider"));
    String oldCurrency = p.getCurrency();
    p.setCurrency(body.currency());
    providers.save(p);

    AdminAction action = new AdminAction();
    action.setActorUserId(admin.id());
    action.setAction("provider.change_currency");
    action.setTargetId(providerId);
    action.setReason(body.reason());
    try {
      action.setPayload(
          json.writeValueAsString(
              Map.of("old", oldCurrency, "new", body.currency())));
    } catch (Exception e) {
      throw new IllegalStateException("could not serialize audit payload", e);
    }
    AdminAction saved = auditLog.save(action);

    return ResponseEntity.ok(Map.of("auditId", saved.getId()));
  }

  public record OverrideBody(
      @NotBlank @Size(min = 3, max = 3) String currency,
      @NotBlank @Size(min = 10) String reason) {}
}
