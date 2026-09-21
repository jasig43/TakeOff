package com.takeoff.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.takeoff.backend.config.AdminAccountSeeder;
import com.takeoff.backend.config.TakeoffProperties;
import com.takeoff.backend.config.TakeoffProperties.Admin;
import com.takeoff.backend.config.TakeoffProperties.Seed;
import com.takeoff.backend.model.Role;
import com.takeoff.backend.model.User;
import com.takeoff.backend.repository.UserRepository;

class AdminAccountSeederTest {

	private static final String EMAIL = "admin@takeoff.co.zw";
	private static final String CONFIGURED = "Configured-Admin-Password#2026";
	private static final String ORIGINAL = "Original-Admin-Password#2026";

	private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
	private UserRepository users;

	@BeforeEach
	void setUp() {
		users = mock(UserRepository.class);
	}

	private void run(boolean enabled, String password, boolean resetPassword) {
		TakeoffProperties base = TestFixtures.properties(false, false);
		TakeoffProperties properties = new TakeoffProperties(base.jwt(), base.cors(), base.otp(), base.rabbitmq(),
				new Admin(new Seed(enabled, " Admin@Takeoff.co.zw ", password, "+15550100", "Demo Admin")), base.driver(),
				base.sms(), base.storage());
		new AdminAccountSeeder(properties, users, encoder, resetPassword).run(new DefaultApplicationArguments());
	}

	private User existing(Role role, String password) {
		User user = new User("Demo Admin", EMAIL, "+15550100", encoder.encode(password), role);
		when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
		return user;
	}

	@Test
	void createsAPhoneVerifiedAdministratorWithAHashedPassword() {
		run(true, CONFIGURED, false);

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(users).save(saved.capture());
		assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
		assertThat(saved.getValue().getRole()).isEqualTo(Role.LOGISTICS_ADMIN);
		assertThat(saved.getValue().isPhoneVerified()).isTrue();
		assertThat(encoder.matches(CONFIGURED, saved.getValue().getPasswordHash())).isTrue();
	}

	@Test
	void neverOverwritesAnExistingAdministratorByDefault_evenWhenTheConfiguredPasswordDiffers() {
		User admin = existing(Role.LOGISTICS_ADMIN, ORIGINAL);

		run(true, CONFIGURED, false);

		verify(users, never()).save(any());
		assertThat(encoder.matches(ORIGINAL, admin.getPasswordHash())).isTrue();
	}

	@Test
	void doesNothingUnlessEnabled_evenIfAResetIsRequested() {
		existing(Role.LOGISTICS_ADMIN, ORIGINAL);

		run(false, CONFIGURED, true);

		verify(users, never()).save(any());
	}

	@Test
	void recovery_putsTheConfiguredPasswordOnTheExistingAdministrator() {
		User admin = existing(Role.LOGISTICS_ADMIN, ORIGINAL);

		run(true, CONFIGURED, true);

		verify(users).save(admin);
		assertThat(encoder.matches(CONFIGURED, admin.getPasswordHash())).isTrue();
		assertThat(encoder.matches(ORIGINAL, admin.getPasswordHash())).isFalse();
		assertThat(admin.getRole()).isEqualTo(Role.LOGISTICS_ADMIN);
	}

	@Test
	void recovery_alsoEndsAnUnfinishedTemporaryPasswordAndReEnablesTheAccount() {
		User admin = existing(Role.LOGISTICS_ADMIN, ORIGINAL);
		admin.issueTemporaryPassword(encoder.encode("Temp-Password-For-Admin#1"), Instant.parse("2020-01-01T00:00:00Z"));
		admin.setEnabled(false);

		run(true, CONFIGURED, true);

		assertThat(admin.isMustChangePassword()).isFalse();
		assertThat(admin.getTemporaryPasswordExpiresAt()).isNull();
		assertThat(admin.isEnabled()).isTrue();
		assertThat(encoder.matches(CONFIGURED, admin.getPasswordHash())).isTrue();
	}

	@Test
	void recovery_leavesAnAccountAloneWhenItAlreadySignsInWithTheConfiguredPassword() {
		existing(Role.LOGISTICS_ADMIN, CONFIGURED);

		run(true, CONFIGURED, true);

		verify(users, never()).save(any());
	}

	@Test
	void recovery_neverTouchesAnAccountThatIsNotAnAdministrator() {
		User driver = existing(Role.APPLICANT_DRIVER, ORIGINAL);

		run(true, CONFIGURED, true);

		verify(users, never()).save(any());
		assertThat(encoder.matches(ORIGINAL, driver.getPasswordHash())).isTrue();
	}

	@Test
	void recovery_stillRefusesAPasswordThatBreaksThePolicy() {
		User admin = existing(Role.LOGISTICS_ADMIN, ORIGINAL);

		run(true, "short", true);

		verify(users, never()).save(any());
		assertThat(encoder.matches(ORIGINAL, admin.getPasswordHash())).isTrue();
	}

	@Test
	void recovery_createsTheAdministratorNormallyWhenThereIsNoneYet() {
		run(true, CONFIGURED, true);

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(users).save(saved.capture());
		assertThat(saved.getValue().getRole()).isEqualTo(Role.LOGISTICS_ADMIN);
	}
}
