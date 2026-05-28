package com.lastminute.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Injectable clock per spec §3.1.
 *
 * Default UTC clock for production. Tests override with {@code Clock.fixed(...)} via
 * a {@code @TestConfiguration} or by replacing the bean.
 *
 * <p>All business logic that needs "now" must inject this {@link Clock} and call
 * {@code Instant.now(clock)} — never the static {@code Instant.now()}. ArchUnit
 * enforces this via {@code ClockUsageTest}.
 */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
