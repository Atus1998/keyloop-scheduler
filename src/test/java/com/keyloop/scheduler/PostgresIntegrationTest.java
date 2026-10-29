package com.keyloop.scheduler;

import com.keyloop.scheduler.testsupport.TestClockConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Spring context on a real PostgreSQL, with the schema migrations but without the seed data, and
 * the fixed {@link TestClockConfiguration#NOW} clock.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration")
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
public @interface PostgresIntegrationTest {}
