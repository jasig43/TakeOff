package com.takeoff.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.takeoff.backend.config.DriverAccountSeeder;
import com.takeoff.backend.config.TakeoffProperties;
import com.takeoff.backend.config.TakeoffProperties.Driver;
import com.takeoff.backend.config.TakeoffProperties.Seed;
import com.takeoff.backend.model.Role;
import com.takeoff.backend.model.User;
import com.takeoff.backend.repository.UserRepository;

class DriverAccountSeederTest {

	private static final String EMAIL = "driver@takeoff.co.zw";
	private static final String PASSWORD = "Test-Driver-Password#2026";

	private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
	private UserRepository users;

	@BeforeEach
	void setUp() {
		users = mock(UserRepository.class);
	}

	private DriverAccountSeeder seeder(Seed seed, String... profiles) {
		TakeoffProperties base = TestFixtures.properties(false, false);
		TakeoffProperties properties = new TakeoffProperties(base.jwt(), base.cors(), base.otp(), base.rabbitmq(),
				base.admin(), new Driver(seed), base.sms(), base.storage());
		return new DriverAccountSeeder(properties, users, encoder, TestFixtures.environment(profiles));
	}

	private static Seed seed(boolean enabled, String email, String password, String phone, String name) {
		return new Seed(enabled, email, password, phone, name);
	}

	private void run(DriverAccountSeeder seeder) {
		seeder.run(new DefaultApplicationArguments());
	}

	@Test
	void createsAPhoneVerifiedDriverWithAHashedPassword() {
		run(seeder(seed(true, " Driver@Takeoff.co.zw ", PASSWORD, "+15550101", "Demo Driver"), "dev"));

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(users).save(saved.capture());
		User driver = saved.getValue();
		assertThat(driver.getEmail()).isEqualTo(EMAIL); // trimmed and lower-cased
		assertThat(driver.getRole()).isEqualTo(Role.APPLICANT_DRIVER);
		assertThat(driver.isPhoneVerified()).isTrue(); // so they can sign in without an OTP
		assertThat(driver.getFullName()).isEqualTo("Demo Driver");
		assertThat(driver.getPasswordHash()).isNotEqualTo(PASSWORD);
		assertThat(encoder.matches(PASSWORD, driver.getPasswordHash())).isTrue();
	}

	@Test
	void fallsBackToADefaultNameWhenNoneIsGiven() {
		run(seeder(seed(true, EMAIL, PASSWORD, "+15550101", ""), "dev"));

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(users).save(saved.capture());
		assertThat(saved.getValue().getFullName()).isEqualTo("TakeOFF Driver");
	}

	@Test
	void doesNothingUnlessEnabled() {
		run(seeder(seed(false, EMAIL, PASSWORD, "+15550101", "Demo Driver"), "dev"));
		verify(users, never()).save(any());
	}

	@Test
	void isAlsoAllowedInTheTestProfile() {
		run(seeder(seed(true, EMAIL, PASSWORD, "+15550101", "Demo Driver"), "test"));
		verify(users).save(any());
	}

	@Test
	void neverCreatesAKnownPasswordAccountOutsideDevAndTest() {
		run(seeder(seed(true, EMAIL, PASSWORD, "+15550101", "Demo Driver"), "prod"));
		run(seeder(seed(true, EMAIL, PASSWORD, "+15550101", "Demo Driver")));
		verify(users, never()).save(any());
	}

	@Test
	void neverOverwritesAnExistingAccount() {
		when(users.existsByEmail(EMAIL)).thenReturn(true);
		run(seeder(seed(true, EMAIL, PASSWORD, "+15550101", "Demo Driver"), "dev"));
		verify(users, never()).save(any());
	}

	@Test
	void skipsWhenThePhoneNumberIsAlreadyTaken() {
		when(users.existsByPhoneNumber("+15550101")).thenReturn(true);
		run(seeder(seed(true, EMAIL, PASSWORD, "+15550101", "Demo Driver"), "dev"));
		verify(users, never()).save(any());
	}

	@Test
	void refusesAPasswordThatBreaksThePolicy() {
		run(seeder(seed(true, EMAIL, "short", "+15550101", "Demo Driver"), "dev"));
		run(seeder(seed(true, EMAIL, "alllowercase-but-long-enough!", "+15550101", "Demo Driver"), "dev"));
		verify(users, never()).save(any());
	}

	@Test
	void refusesIncompleteConfiguration() {
		run(seeder(seed(true, "", PASSWORD, "+15550101", "Demo Driver"), "dev"));
		run(seeder(seed(true, EMAIL, "", "+15550101", "Demo Driver"), "dev"));
		run(seeder(seed(true, EMAIL, PASSWORD, "", "Demo Driver"), "dev"));
		verify(users, never()).save(any());
	}

	@Test
	void theSeedNeverPrintsItsPassword() {
		assertThat(seed(true, EMAIL, PASSWORD, "+15550101", "Demo Driver").toString()).doesNotContain(PASSWORD);
	}
}
