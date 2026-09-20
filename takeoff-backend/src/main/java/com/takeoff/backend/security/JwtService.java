package com.takeoff.backend.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.takeoff.backend.config.TakeoffProperties;
import com.takeoff.backend.model.User;

/**
 * Issues and validates HS256-signed JWTs. Refuses to start without a strong secret, and refuses
 * the built-in development secret outside the dev/test profiles.
 */
@Service
public class JwtService {

	static final String CLAIM_EMAIL = "email";
	static final String CLAIM_AUTHORITIES = "authorities";
	private static final int MIN_SECRET_BYTES = 32;
	private static final String DEV_SECRET_PREFIX = "dev-only-";

	private final JwtEncoder encoder;
	private final JwtDecoder decoder;
	private final String issuer;
	private final Duration lifetime;
	private final Clock clock;

	public JwtService(TakeoffProperties properties, Environment environment, Clock clock) {
		TakeoffProperties.Jwt jwt = properties.jwt();
		String secret = jwt.secret();
		boolean devOrTest = environment.acceptsProfiles(Profiles.of("dev", "test"));

		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException("JWT_SECRET is not configured. Set the JWT_SECRET environment variable to a "
					+ "random string of at least " + MIN_SECRET_BYTES + " characters (for example: openssl rand -base64 48), "
					+ "or start the application with SPRING_PROFILES_ACTIVE=dev for local development.");
		}
		if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("JWT_SECRET is too short: HS256 needs at least " + MIN_SECRET_BYTES
					+ " bytes of secret material.");
		}
		if (secret.startsWith(DEV_SECRET_PREFIX) && !devOrTest) {
			throw new IllegalStateException("The built-in development JWT secret must not be used outside the dev/test "
					+ "profiles. Set JWT_SECRET to a private random value.");
		}

		SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));

		NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		nimbusDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ofSeconds(5)),
				new JwtIssuerValidator(jwt.issuer())));
		this.decoder = nimbusDecoder;

		this.issuer = jwt.issuer();
		this.lifetime = Duration.ofMinutes(jwt.expirationMinutes());
		this.clock = clock;
	}

	/** Lifetime of newly issued tokens, in seconds. */
	public long lifetimeSeconds() {
		return lifetime.toSeconds();
	}

	public String generateToken(User user) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(issuer)
			.subject(String.valueOf(user.getId()))
			.issuedAt(now)
			.expiresAt(now.plus(lifetime))
			.claim(CLAIM_EMAIL, user.getEmail())
			.claim(CLAIM_AUTHORITIES, List.of(user.getRole().authority()))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	/**
	 * Verifies signature, issuer and expiry.
	 *
	 * @throws JwtException if the token is malformed, tampered with, expired or from another issuer
	 */
	public Jwt parse(String token) throws JwtException {
		return decoder.decode(token);
	}
}
