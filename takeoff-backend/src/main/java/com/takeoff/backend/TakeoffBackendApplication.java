package com.takeoff.backend;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Profiles;

import com.takeoff.backend.security.JwtService;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TakeoffBackendApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(TakeoffBackendApplication.class);
		// Preflight: runs once profiles and configuration are loaded but before any bean (including the
		// database connection) is created, so a missing JWT secret is the first thing reported.
		application.addListeners((ApplicationEnvironmentPreparedEvent event) -> JwtService.validateSecret(
				event.getEnvironment().getProperty("takeoff.jwt.secret"),
				event.getEnvironment().acceptsProfiles(Profiles.of("dev", "test"))));
		application.run(args);
	}

	/** Single UTC clock for all timestamps; replaceable in tests. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}
