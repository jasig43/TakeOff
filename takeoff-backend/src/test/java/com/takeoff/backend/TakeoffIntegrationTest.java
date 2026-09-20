package com.takeoff.backend;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

/**
 * Full-context integration test on the {@code test} profile (in-memory H2, mocked RabbitMQ).
 *
 * <p>{@code spring.config.location=optional:classpath:/} restricts configuration to the classpath. Without it Spring
 * also reads {@code ./config/application.properties} from the working directory, where developers keep machine-local
 * settings (database URL and password, JWT secret, admin seed). Tests must never be affected by, or able to reach, a
 * developer's real database, so that folder is deliberately ignored here.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "spring.config.location=optional:classpath:/")
@AutoConfigureMockMvc
@ActiveProfiles("test")
public @interface TakeoffIntegrationTest {
}
