package com.lastminute.auth;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain chain(HttpSecurity http) throws Exception {
    return http
        // For M1, disable CSRF for the magic-link request endpoint (no body except an email
        // address; anyone can ask for a magic link). When we add price-sensitive Server Actions
        // in M3, re-introduce CSRF for those routes specifically via a CookieCsrfTokenRepository.
        // M1: admin endpoints are role-gated; M3 will introduce CSRF for consumer state-changing
        // endpoints. /api/auth, /api/webhooks, /api/admin, /api/providers are by design CSRF-free.
        .csrf(
            c ->
                c.ignoringRequestMatchers(
                    "/api/auth/**",
                    "/api/webhooks/**",
                    "/api/admin/**",
                    "/api/providers/**"))
        .cors(Customizer.withDefaults())
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/auth/**",
                        "/api/listings/**",
                        "/api/categories/**",
                        "/api/providers/signup",
                        "/api/webhooks/**",
                        "/actuator/health",
                        "/actuator/info")
                    .permitAll()
                    .requestMatchers("/api/admin/**")
                    .hasAuthority("ROLE_ADMIN")
                    .requestMatchers("/api/providers/me/**", "/api/providers/onboarding/**")
                    .hasAuthority("ROLE_PROVIDER")
                    .anyRequest()
                    .authenticated())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .formLogin(f -> f.disable())
        .httpBasic(b -> b.disable())
        .build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${app.frontend-origin:http://localhost:5173}") String frontendOrigin) {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(List.of(frontendOrigin));
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    cfg.setAllowedHeaders(List.of("*"));
    cfg.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
    src.registerCorsConfiguration("/**", cfg);
    return src;
  }
}
