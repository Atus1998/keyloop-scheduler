package com.keyloop.scheduler;

import com.keyloop.scheduler.testsupport.TestClockConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;

/**
 * Like {@link PostgresIntegrationTest}, but with a real server on a random port, for tests that
 * send truly parallel HTTP requests. The pool has room for every parallel request plus the test's
 * own queries.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.flyway.locations=classpath:db/migration",
      "spring.datasource.hikari.maximum-pool-size=12"
    })
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
public @interface PostgresServerTest {}
