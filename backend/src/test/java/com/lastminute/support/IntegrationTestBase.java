package com.lastminute.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for integration tests. Spins up one Postgres container per test JVM (Surefire forks one
 * JVM by default; with {@code withReuse(true)} it persists across runs locally).
 *
 * <p>Tests extending this class also get {@link DbTruncate} injected via {@link #truncate}, called
 * in {@code @BeforeEach} to wipe business tables between tests in FK-safe order.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class IntegrationTestBase {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine").withReuse(true);

  @Autowired private DbTruncate truncate;

  @BeforeEach
  void resetDb() {
    truncate.truncateAll();
  }
}
