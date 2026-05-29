package com.lastminute.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for integration tests.
 *
 * <p>Starts ONE Postgres container per JVM (no JUnit @Container/@Testcontainers lifecycle so it
 * isn't torn down between test classes — that re-binds the random port and breaks Spring's
 * cached datasource bindings). The container is started in a static block and lives for the
 * lifetime of the surefire fork; {@code @ServiceConnection} wires Spring's DataSource to it.
 *
 * <p>Tests extending this class get {@link DbTruncate} injected via {@link #truncate}, called in
 * {@code @BeforeEach} to wipe business tables AND the listing caches between tests in FK-safe
 * order.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine").withReuse(true);

  static {
    POSTGRES.start();
  }

  @Autowired private DbTruncate truncate;

  @BeforeEach
  void resetDb() {
    truncate.truncateAll();
  }
}
