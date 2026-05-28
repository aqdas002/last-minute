package com.lastminute.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Spec §3.1 enforcement: outside the clock module, do not call the static
 * {@code Instant.now()} / {@code LocalDateTime.now()} / {@code OffsetDateTime.now()}.
 * Inject {@link java.time.Clock} and call {@code Instant.now(clock)} instead.
 *
 * <p>Implemented as a plain JUnit test (calling ArchUnit programmatically) rather than the
 * {@code @ArchTest}-field style, because Surefire 3.5 + Spring Boot 3.5 Jupiter integration
 * doesn't reliably hand discovery to the {@code archunit-junit5-engine}.
 *
 * <p>Allowlist:
 * <ul>
 *   <li>{@code com.lastminute.common} — the clock bean itself</li>
 *   <li>test packages (excluded from importer below)</li>
 * </ul>
 */
class ClockUsageTest {

  private static final JavaClasses PRODUCTION_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.lastminute");

  @Test
  void no_raw_instant_now_in_business_logic() {
    ArchRule rule =
        noClasses()
            .that()
            .resideOutsideOfPackages("com.lastminute.common..")
            .should()
            .callMethod(java.time.Instant.class, "now")
            .orShould()
            .callMethod(java.time.LocalDateTime.class, "now")
            .orShould()
            .callMethod(java.time.OffsetDateTime.class, "now")
            .because("use injected Clock: Instant.now(clock). See spec §3.1.");
    rule.check(PRODUCTION_CLASSES);
  }
}
