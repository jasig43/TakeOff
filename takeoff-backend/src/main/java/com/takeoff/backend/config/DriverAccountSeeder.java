package com.takeoff.backend.config;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.takeoff.backend.model.Role;
import com.takeoff.backend.model.User;
import com.takeoff.backend.repository.UserRepository;
import com.takeoff.backend.validation.PasswordPolicy;

/**
 * Creates one ready-made APPLICANT_DRIVER account from configuration ({@code takeoff.driver.seed.*}) so a developer or
 * evaluator can sign in as a driver without registering and receiving an OTP. The account is created with its phone
 * already verified.
 *
 * <p>Because it is a known credential it only ever runs in the {@code dev} and {@code test} profiles, it does nothing
 * unless explicitly enabled, it never overwrites an existing account, and it applies the same password policy as
 * registration. Real drivers always come through public sign-up.
 */
@Component
public class DriverAccountSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DriverAccountSeeder.class);

	private final TakeoffProperties.Seed seed;
	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final boolean devOrTest;

	public DriverAccountSeeder(TakeoffProperties properties, UserRepository users, PasswordEncoder passwordEncoder,
			Environment environment) {
		this.seed = properties.driver().seed();
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.devOrTest = environment.acceptsProfiles(Profiles.of("dev", "test"));
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!seed.enabled()) {
			return;
		}
		if (!devOrTest) {
			log.error("Driver seeding is enabled but the dev or test profile is not active. A known-password account is "
					+ "never created elsewhere. No driver account was created.");
			return;
		}
		if (isBlank(seed.email()) || isBlank(seed.password()) || isBlank(seed.phoneNumber())) {
			log.error("Driver seeding is enabled but DRIVER_SEED_EMAIL, DRIVER_SEED_PASSWORD and DRIVER_SEED_PHONE are not "
					+ "all set. No driver account was created.");
			return;
		}
		List<String> violations = PasswordPolicy.violations(seed.password());
		if (!violations.isEmpty()) {
			log.error("DRIVER_SEED_PASSWORD does not meet the password policy ({}). No driver account was created.",
					String.join(" ", violations));
			return;
		}

		String email = seed.email().trim().toLowerCase(Locale.ROOT);
		String phone = seed.phoneNumber().trim();
		if (users.existsByEmail(email) || users.existsByPhoneNumber(phone)) {
			log.info("Driver account already present; seeding skipped.");
			return;
		}

		String name = isBlank(seed.fullName()) ? "TakeOFF Driver" : seed.fullName().trim();
		User driver = new User(name, email, phone, passwordEncoder.encode(seed.password()), Role.APPLICANT_DRIVER);
		driver.setPhoneVerified(true); // seeded accounts skip the OTP step
		users.save(driver);
		log.info("Seeded APPLICANT_DRIVER account {}", email);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
