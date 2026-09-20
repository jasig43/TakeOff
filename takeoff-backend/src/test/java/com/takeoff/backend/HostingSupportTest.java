package com.takeoff.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.takeoff.backend.config.EnvironmentProfiles;
import com.takeoff.backend.security.JwtService;

/** What the hosted demo relies on: a public health probe, and the rules about which profile may do what. */
@TakeoffIntegrationTest
class HostingSupportTest {

	private static final String BUILT_IN_DEV_SECRET = "dev-only-insecure-jwt-secret-change-me-0123456789abcdef";

	@Autowired
	MockMvc mvc;

	@MockitoBean
	RabbitTemplate rabbitTemplate;

	@Test
	void theHealthProbeIsPublicAndSaysNothingAboutTheData() throws Exception {
		mvc.perform(get("/api/v1/health")).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.service").value("takeoff-backend"))
			.andExpect(jsonPath("$.users").doesNotExist());
	}

	@Test
	void onlyTheProbeIsOpen_otherPathsStillNeedASession() throws Exception {
		mvc.perform(get("/api/v1/drivers/profile")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/something-else")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/health")).andExpect(status().is4xxClientError()); // read-only
	}

	@Test
	void theDemoProfileGetsTheDevConveniencesButNotTheBuiltInSecret() {
		for (String profile : new String[] { "dev", "demo", "test" }) {
			assertThat(TestFixtures.environment(profile).acceptsProfiles(EnvironmentProfiles.RELAXED)).as(profile).isTrue();
		}
		assertThat(TestFixtures.environment("prod").acceptsProfiles(EnvironmentProfiles.RELAXED)).isFalse();
		assertThat(TestFixtures.environment().acceptsProfiles(EnvironmentProfiles.RELAXED)).isFalse();

		// the built-in JWT secret stays limited to dev and test: a public demo must bring a real one
		assertThatCode(() -> new JwtService(TestFixtures.withJwtSecret(BUILT_IN_DEV_SECRET), TestFixtures.environment("dev"), Clock.systemUTC()))
			.doesNotThrowAnyException();
		assertThatThrownBy(() -> new JwtService(TestFixtures.withJwtSecret(BUILT_IN_DEV_SECRET), TestFixtures.environment("demo"), Clock.systemUTC()))
			.isInstanceOf(IllegalStateException.class);
		assertThatCode(() -> new JwtService(TestFixtures.withJwtSecret("a-real-secret-of-at-least-thirty-two-characters!"),
				TestFixtures.environment("demo"), Clock.systemUTC())).doesNotThrowAnyException();
	}
}
