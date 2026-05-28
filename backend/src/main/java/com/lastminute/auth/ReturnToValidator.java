package com.lastminute.auth;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Spec §3.3 single source of truth for the {@code return_to} allowlist.
 *
 * <p>The frontend has its own copy of this regex at {@code frontend/src/lib/return-to.ts}; a
 * parity test verifies the two stay in sync.
 */
@Component
public class ReturnToValidator {

  private static final Pattern ALLOW =
      Pattern.compile(
          "^/(?:$"
              + "|c/[a-z0-9-]+(?:\\?[^#]*)?"
              + "|bookings/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?:\\?[^#]*)?"
              + "|book/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
              + "|provider/(?:dashboard|onboarding|bookings|listings)(?:/[a-z0-9-]+)?(?:\\?[^#]*)?"
              + ")$");

  public boolean isAllowed(String path) {
    if (path == null || path.isEmpty()) return false;
    if (path.contains("\\")) return false;
    if (path.startsWith("//")) return false;
    return ALLOW.matcher(path).matches();
  }

  public String safe(String path) {
    return isAllowed(path) ? path : "/";
  }
}
