package com.takeoff.backend.config;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.takeoff.backend.model.Role;
import com.takeoff.backend.model.User;
import com.takeoff.backend.repository.UserRepository;
import com.takeoff.backend.validation.PasswordPolicy;

/**
 * Controlled bootstrap of a LOGISTICS_ADMIN account. Public registration can never create admins,
 * so the first one is seeded from configuration ({@code takeoff.admin.seed.*}). It does nothing
 * unless explicitly enabled, never overwrites an existing account, and applies the same password
 * policy as registration.
 */
@Component
public class AdminAccountSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminAccountSeeder.class);

	private final TakeoffProperties.Seed seed;
	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;

	public AdminAccountSeeder(TakeoffProperties properties, UserRepository users, PasswordEncoder passwordEncoder) {
		this.seed = properties.admin().seed();
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!seed.enabled()) {
			return;
		}
		if (isBlank(seed.email()) || isBlank(seed.password()) || isBlank(seed.phoneNumber())) {
			log.error("Admin seeding is enabled but ADMIN_SEED_EMAIL, ADMIN_SEED_PASSWORD and ADMIN_SEED_PHONE are not "
					+ "all set. No admin account was created.");
			return;
		}
		List<String> violations = PasswordPolicy.violations(seed.password());
		if (!violations.isEmpty()) {
			log.error("ADMIN_SEED_PASSWORD does not meet the password policy ({}). No admin account was created.",
					String.join(" ", violations));
			return;
		}

		String email = seed.email().trim().toLowerCase(Locale.ROOT);
		if (users.existsByEmail(email) || users.existsByPhoneNumber(seed.phoneNumber().trim())) {
			log.info("Admin account already present; seeding skipped.");
			return;
		}

		User admin = new User(seed.fullName(), email, seed.phoneNumber().trim(), passwordEncoder.encode(seed.password()),
				Role.LOGISTICS_ADMIN);
		admin.setPhoneVerified(true); // admins do not go through the driver OTP flow
		users.save(admin);
		log.info("Seeded LOGISTICS_ADMIN account {}", email);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
