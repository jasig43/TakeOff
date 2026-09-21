package com.takeoff.backend.config;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * <p>
 * The one exception is recovery: when the seeded administrator can no longer sign in (the configured password was
 * changed after the account was created, or was lost) and nobody can reach the database, setting
 * {@code takeoff.admin.reset-password=true} makes the next start-up put the configured password on that account. It
 * applies only to an existing LOGISTICS_ADMIN account with the configured email, says so in the log, and should be
 * switched off again afterwards, because while it is on it also undoes any password the administrator chose later.
 */
@Component
public class AdminAccountSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminAccountSeeder.class);

	private final TakeoffProperties.Seed seed;
	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final boolean resetPassword;

	public AdminAccountSeeder(TakeoffProperties properties, UserRepository users, PasswordEncoder passwordEncoder,
			@Value("${takeoff.admin.reset-password:false}") boolean resetPassword) {
		this.seed = properties.admin().seed();
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.resetPassword = resetPassword;
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
		Optional<User> existing = users.findByEmail(email);
		if (existing.isPresent()) {
			if (resetPassword) {
				resetPasswordOf(existing.get());
			}
			else {
				log.info("Admin account already present; seeding skipped.");
			}
			return;
		}
		if (users.existsByPhoneNumber(seed.phoneNumber().trim())) {
			log.info("Admin account already present; seeding skipped.");
			return;
		}

		User admin = new User(seed.fullName(), email, seed.phoneNumber().trim(), passwordEncoder.encode(seed.password()),
				Role.LOGISTICS_ADMIN);
		admin.setPhoneVerified(true); // admins do not go through the driver OTP flow
		users.save(admin);
		log.info("Seeded LOGISTICS_ADMIN account {}", email);
	}

	private void resetPasswordOf(User account) {
		if (account.getRole() != Role.LOGISTICS_ADMIN) {
			log.error("ADMIN_SEED_RESET_PASSWORD is on, but {} is not an administrator account. Nothing was changed.",
					account.getEmail());
			return;
		}
		if (passwordEncoder.matches(seed.password(), account.getPasswordHash()) && !account.isMustChangePassword()) {
			log.warn("ADMIN_SEED_RESET_PASSWORD is on, but {} already signs in with ADMIN_SEED_PASSWORD. Turn the "
					+ "setting off.", account.getEmail());
			return;
		}
		account.changePassword(passwordEncoder.encode(seed.password())); // also clears any temporary-password state
		account.setEnabled(true);
		users.save(account);
		log.warn("ADMIN_SEED_RESET_PASSWORD is on: the password of {} was set from ADMIN_SEED_PASSWORD. Turn the "
				+ "setting off, or it will undo any password chosen later.", account.getEmail());
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
