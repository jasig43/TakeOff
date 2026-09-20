package com.takeoff.backend;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TakeoffBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(TakeoffBackendApplication.class, args);
	}

	/** Single UTC clock for all timestamps; replaceable in tests. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}
