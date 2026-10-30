package com.keyloop.scheduler;

import org.springframework.boot.SpringApplication;

/** {@code ./gradlew bootTestRun}: the app on a throwaway PostgreSQL, with the demo data. */
public class TestSchedulerApplication {

  public static void main(String[] args) {
    SpringApplication.from(SchedulerApplication::main)
        .with(TestcontainersConfiguration.class)
        .withAdditionalProfiles("demo")
        .run(args);
  }
}
