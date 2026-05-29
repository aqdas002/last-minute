package com.lastminute.auth;

import com.lastminute.users.User;
import com.lastminute.users.UserRepository;
import com.lastminute.users.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class MagicLinkController {

  private static final Logger LOG = LoggerFactory.getLogger(MagicLinkController.class);

  private final MagicLinkService magic;
  private final ReturnToValidator returnTo;
  private final UserRepository users;
  private final String frontendOrigin;

  public MagicLinkController(
      MagicLinkService magic,
      ReturnToValidator returnTo,
      UserRepository users,
      @Value("${app.frontend-origin:http://localhost:5173}") String frontendOrigin) {
    this.magic = magic;
    this.returnTo = returnTo;
    this.users = users;
    this.frontendOrigin = frontendOrigin;
  }

  public record MagicLinkRequest(
      @NotBlank @Email String email, @RequestParam(required = false) String returnTo) {}

  /** Anyone can request a magic link to be emailed; the security is in the email delivery. */
  @PostMapping("/magic/request")
  public ResponseEntity<Void> request(@Valid @RequestBody MagicLinkRequest body) {
    magic.request(body.email(), returnTo.safe(body.returnTo()));
    return ResponseEntity.accepted().build();
  }

  /** Callback consumed when the user clicks the email link. */
  @GetMapping("/magic")
  @Transactional
  public ResponseEntity<Void> consume(
      @RequestParam String token,
      @RequestParam(name = "return_to", required = false) String returnToParam,
      HttpServletRequest req) {
    try {
      String email = magic.consume(token);
      User user =
          users
              .findByEmail(email)
              .orElseGet(
                  () -> {
                    User u = new User();
                    u.setEmail(email);
                    u.setRole(UserRole.consumer);
                    return users.save(u);
                  });

      CurrentUser principal = new CurrentUser(user.getId(), user.getEmail(), user.getRole());
      var auth =
          new UsernamePasswordAuthenticationToken(
              principal,
              null,
              List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name().toUpperCase())));
      SecurityContext ctx = SecurityContextHolder.createEmptyContext();
      ctx.setAuthentication(auth);
      SecurityContextHolder.setContext(ctx);
      req.getSession(true)
          .setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);

      String safe = returnTo.safe(returnToParam);
      return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(frontendOrigin + safe)).build();
    } catch (InvalidTokenException e) {
      LOG.info("magic-link rejected: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.FOUND)
          .location(URI.create(frontendOrigin + "/signin?error=" + e.getMessage()))
          .build();
    }
  }
}
