package com.lastminute;

import com.lastminute.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

class LastMinuteApplicationTests extends IntegrationTestBase {

  @Test
  void contextLoads() {
    // smoke: Spring boots with Postgres (Testcontainers) + Flyway + JPA + Security.
  }
}
