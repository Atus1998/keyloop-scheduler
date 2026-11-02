package com.keyloop.scheduler;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

  private static final String DOMAIN = "com.keyloop.scheduler.domain..";
  private static final String APPLICATION = "com.keyloop.scheduler.application..";
  private static final String ADAPTER = "com.keyloop.scheduler.adapter..";

  private static final JavaClasses PRODUCTION_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.keyloop.scheduler");

  @Test
  void domainDoesNotDependOnSpringOrJpa() {
    noSpringOrJpaIn(DOMAIN).check(PRODUCTION_CLASSES);
  }

  @Test
  void applicationDoesNotDependOnSpringOrJpa() {
    noSpringOrJpaIn(APPLICATION).check(PRODUCTION_CLASSES);
  }

  @Test
  void applicationDoesNotDependOnAdapters() {
    noClasses()
        .that()
        .resideInAPackage(APPLICATION)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(ADAPTER)
        .check(PRODUCTION_CLASSES);
  }

  @Test
  void domainDoesNotDependOnApplicationOrAdapters() {
    noClasses()
        .that()
        .resideInAPackage(DOMAIN)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(APPLICATION, ADAPTER)
        .check(PRODUCTION_CLASSES);
  }

  @Test
  void domainAndApplicationDoNotDependOnHibernate() {
    noClasses()
        .that()
        .resideInAnyPackage(DOMAIN, APPLICATION)
        .should()
        .dependOnClassesThat()
        .resideInAPackage("org.hibernate..")
        .check(PRODUCTION_CLASSES);
  }

  @Test
  void domainAndApplicationDoNotDependOnOpenApi() {
    noClasses()
        .that()
        .resideInAnyPackage(DOMAIN, APPLICATION)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("io.swagger..", "org.springdoc..")
        .check(PRODUCTION_CLASSES);
  }

  @Test
  void inboundAdaptersDoNotDependOnOutboundAdapters() {
    noClasses()
        .that()
        .resideInAPackage("com.keyloop.scheduler.adapter.in..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.keyloop.scheduler.adapter.out..")
        .check(PRODUCTION_CLASSES);
  }

  @Test
  void rulesDetectASpringDependency() {
    assertThatThrownBy(
            () ->
                noSpringOrJpaIn("com.keyloop.scheduler.adapter.out.persistence..")
                    .check(PRODUCTION_CLASSES))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void rulesSeeDomainAndApplicationClasses() {
    assertThat(PRODUCTION_CLASSES.that(resideInAPackage(DOMAIN))).isNotEmpty();
    assertThat(PRODUCTION_CLASSES.that(resideInAPackage(APPLICATION))).isNotEmpty();
  }

  private static ArchRule noSpringOrJpaIn(String packageIdentifier) {
    return noClasses()
        .that()
        .resideInAPackage(packageIdentifier)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework..", "jakarta.persistence..");
  }
}
