package com.takeoff.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import com.takeoff.backend.model.Role;
import com.takeoff.backend.model.User;
import com.takeoff.backend.security.JwtService;

class JwtServiceTest {

	private static JwtService service(String secret, String profile, Clock clock) {
		return new JwtService(TestFixtures.withJwtSecret(secret), TestFixtures.environment(profile), clock);
	}

	private static JwtService service() {
		return service(TestFixtures.JWT_SECRET, "test", Clock.systemUTC());
	}

	private static User user(Role role) {
		User user = new User("Test", "person@example.com", "+15550123", "hash", role);
		ReflectionTestUtils.setField(user, "id", 42L);
		return user;
	}

	@Test
	void tokenCarriesSubjectEmailAuthoritiesAndTimes() {
		JwtService jwtService = service();

		Jwt jwt = jwtService.parse(jwtService.generateToken(user(Role.APPLICANT_DRIVER)));

		assertThat(jwt.getSubject()).isEqualTo("42");
		assertThat(jwt.getClaimAsString("email")).isEqualTo("person@example.com");
		assertThat(jwt.getClaimAsStringList("authorities")).containsExactly("ROLE_APPLICANT_DRIVER");
		assertThat(jwt.getIssuedAt()).isNotNull();
		assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
		assertThat(jwtService.lifetimeSeconds()).isEqualTo(900);
	}

	@Test
	void adminTokensCarryTheAdminAuthority() {
		JwtService jwtService = service();
		Jwt jwt = jwtService.parse(jwtService.generateToken(user(Role.LOGISTICS_ADMIN)));
		assertThat(jwt.getClaimAsStringList("authorities")).isEqualTo(List.of("ROLE_LOGISTICS_ADMIN"));
	}

	@Test
	void tamperedTokensAreRejected() {
		JwtService jwtService = service();
		String[] parts = jwtService.generateToken(user(Role.APPLICANT_DRIVER)).split("\\.");
		char original = parts[1].charAt(10);
		String forgedPayload = parts[1].substring(0, 10) + (original == 'A' ? 'B' : 'A') + parts[1].substring(11);

		assertThatThrownBy(() -> jwtService.parse(parts[0] + "." + forgedPayload + "." + parts[2]))
			.isInstanceOf(JwtException.class);
	}

	@Test
	void tokensSignedWithAnotherSecretAreRejected() {
		String token = service("another-secret-that-is-long-enough-0123456789", "test", Clock.systemUTC())
			.generateToken(user(Role.APPLICANT_DRIVER));
		assertThatThrownBy(() -> service().parse(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void expiredTokensAreRejected() {
		Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
		String expired = service(TestFixtures.JWT_SECRET, "test", twoHoursAgo).generateToken(user(Role.APPLICANT_DRIVER));
		assertThatThrownBy(() -> service().parse(expired)).isInstanceOf(JwtException.class);
	}

	@Test
	void garbageIsRejected() {
		assertThatThrownBy(() -> service().parse("not-a-jwt")).isInstanceOf(JwtException.class);
	}

	@Test
	void startupFailsClearlyWithoutASecret() {
		assertThatThrownBy(() -> service("", "prod", Clock.systemUTC())).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("JWT_SECRET is not configured");
		assertThatThrownBy(() -> service(null, "test", Clock.systemUTC())).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("JWT_SECRET");
	}

	@Test
	void startupFailsWithATooShortSecret() {
		assertThatThrownBy(() -> service("too-short", "prod", Clock.systemUTC())).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("too short");
	}

	@Test
	void theBuiltInDevSecretIsOnlyAcceptedInDevOrTest() {
		String devSecret = "dev-only-insecure-jwt-secret-change-me-0123456789abcdef";

		assertThatThrownBy(() -> service(devSecret, "prod", Clock.systemUTC())).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("development JWT secret");
		assertThatCode(() -> service(devSecret, "dev", Clock.systemUTC())).doesNotThrowAnyException();
	}
}
