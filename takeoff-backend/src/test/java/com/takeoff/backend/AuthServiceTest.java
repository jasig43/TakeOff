package com.takeoff.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

import com.takeoff.backend.config.TakeoffProperties;
import com.takeoff.backend.dto.JwtResponse;
import com.takeoff.backend.dto.LoginRequest;
import com.takeoff.backend.dto.OtpVerifyRequest;
import com.takeoff.backend.dto.RegistrationResponse;
import com.takeoff.backend.dto.ResendOtpRequest;
import com.takeoff.backend.dto.SignUpRequest;
import com.takeoff.backend.exception.ApiException;
import com.takeoff.backend.exception.OtpDispatchException;
import com.takeoff.backend.model.OtpToken;
import com.takeoff.backend.model.Role;
import com.takeoff.backend.model.User;
import com.takeoff.backend.repository.OtpTokenRepository;
import com.takeoff.backend.repository.UserRepository;
import com.takeoff.backend.security.JwtService;
import com.takeoff.backend.service.AuthService;
import com.takeoff.backend.service.OtpCodec;
import com.takeoff.backend.service.OtpProducerService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private static final String PHONE = "+15550123";

	@Mock
	UserRepository users;
	@Mock
	OtpTokenRepository otpTokens;
	@Mock
	OtpProducerService producer;
	@Mock
	JwtService jwtService;

	private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
	private final TakeoffProperties properties = TestFixtures.properties(false, true);
	private AuthService service;

	@BeforeEach
	void setUp() {
		OtpCodec codec = new OtpCodec(properties, TestFixtures.environment("test"));
		service = new AuthService(users, otpTokens, encoder, jwtService, producer, codec,
				TransactionOperations.withoutTransaction(), properties, TestFixtures.CLOCK);
	}

	private static SignUpRequest signUp(String email, String phone) {
		return new SignUpRequest("Test Driver", email, phone, TestFixtures.COMPLIANT_PASSWORD, true);
	}

	private User applicant(boolean verified) {
		User user = new User("Test Driver", "driver@example.com", PHONE,
				encoder.encode(TestFixtures.COMPLIANT_PASSWORD), Role.APPLICANT_DRIVER);
		ReflectionTestUtils.setField(user, "id", 42L);
		user.setPhoneVerified(verified);
		return user;
	}

	private static OtpToken token(String code, Instant expiresAt, boolean consumed, int attempts) {
		OtpToken token = new OtpToken(42L, code, expiresAt, TestFixtures.CLOCK.instant().minusSeconds(60));
		token.setConsumed(consumed);
		token.setAttempts(attempts);
		return token;
	}

	// ---------------------------------------------------------------- registration

	@Test
	void registerCreatesAnApplicantWithAHashedPasswordAndPublishesTheOtpEvent() {
		when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
			User saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 42L);
			return saved;
		});

		RegistrationResponse response = service.register(signUp("  Driver@Example.com ", PHONE));

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(users).saveAndFlush(saved.capture());
		User user = saved.getValue();
		assertThat(user.getRole()).isEqualTo(Role.APPLICANT_DRIVER);
		assertThat(user.getEmail()).isEqualTo("driver@example.com");
		assertThat(user.isPhoneVerified()).isFalse();
		assertThat(user.getPasswordHash()).isNotEqualTo(TestFixtures.COMPLIANT_PASSWORD);
		assertThat(encoder.matches(TestFixtures.COMPLIANT_PASSWORD, user.getPasswordHash())).isTrue();

		verify(producer).publishOtpGenerate(42L, PHONE);
		assertThat(response.otpDispatched()).isTrue();
		assertThat(response.maskedPhone()).endsWith("0123").doesNotContain("5550");
		assertThat(response.toString()).doesNotContain(TestFixtures.COMPLIANT_PASSWORD);
	}

	@Test
	void registerRejectsADuplicateEmailWithConflict() {
		when(users.existsByEmail("driver@example.com")).thenReturn(true);

		assertThatThrownBy(() -> service.register(signUp("driver@example.com", PHONE)))
			.isInstanceOfSatisfying(ApiException.class, ex -> {
				assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
				assertThat(ex.getCode()).isEqualTo("EMAIL_ALREADY_REGISTERED");
			});
		verify(users, never()).saveAndFlush(any());
		verify(producer, never()).publishOtpGenerate(anyLong(), anyString());
	}

	@Test
	void registerRejectsADuplicatePhoneWithConflict() {
		when(users.existsByPhoneNumber(PHONE)).thenReturn(true);

		assertThatThrownBy(() -> service.register(signUp("new@example.com", PHONE)))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("PHONE_ALREADY_REGISTERED"));
		verify(users, never()).saveAndFlush(any());
	}

	@Test
	void registerStillSucceedsWhenTheBrokerIsDownAndReportsOtpNotDispatched() {
		when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
			User saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 42L);
			return saved;
		});
		doThrow(new OtpDispatchException(new RuntimeException("broker down"))).when(producer)
			.publishOtpGenerate(eq(42L), anyString());

		RegistrationResponse response = service.register(signUp("driver@example.com", PHONE));

		assertThat(response.otpDispatched()).isFalse();
		assertThat(response.message()).contains("Resend code");
	}

	// ---------------------------------------------------------------- OTP verification

	private void stubUserAndToken(User user, OtpToken token) {
		when(users.findByEmail("driver@example.com")).thenReturn(Optional.of(user));
		when(otpTokens.findFirstByUserIdOrderByIdDesc(42L)).thenReturn(Optional.of(token));
	}

	@Test
	void verifyOtpWithTheCorrectCodeMarksThePhoneVerifiedAndReturnsAJwt() {
		User user = applicant(false);
		OtpToken token = token("482913", TestFixtures.CLOCK.instant().plusSeconds(120), false, 1);
		stubUserAndToken(user, token);
		when(jwtService.generateToken(user)).thenReturn("signed.jwt.value");
		when(jwtService.lifetimeSeconds()).thenReturn(900L);

		JwtResponse response = service.verifyOtp(new OtpVerifyRequest("driver@example.com", "482913"));

		assertThat(user.isPhoneVerified()).isTrue();
		assertThat(token.isConsumed()).isTrue();
		assertThat(response.accessToken()).isEqualTo("signed.jwt.value");
		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.expiresInSeconds()).isEqualTo(900L);
		assertThat(response.user().role()).isEqualTo(Role.APPLICANT_DRIVER);
		verify(users).save(user);
	}

	@Test
	void verifyOtpAlsoAcceptsThePhoneNumberAsIdentifier() {
		User user = applicant(false);
		OtpToken token = token("482913", TestFixtures.CLOCK.instant().plusSeconds(120), false, 0);
		when(users.findByPhoneNumber(PHONE)).thenReturn(Optional.of(user));
		when(otpTokens.findFirstByUserIdOrderByIdDesc(42L)).thenReturn(Optional.of(token));
		when(jwtService.generateToken(user)).thenReturn("jwt");

		assertThat(service.verifyOtp(new OtpVerifyRequest(PHONE, "482913")).accessToken()).isEqualTo("jwt");
	}

	@Test
	void verifyOtpRejectsAWrongCodeAndCountsTheAttempt() {
		User user = applicant(false);
		OtpToken token = token("482913", TestFixtures.CLOCK.instant().plusSeconds(120), false, 0);
		stubUserAndToken(user, token);

		assertThatThrownBy(() -> service.verifyOtp(new OtpVerifyRequest("driver@example.com", "000000")))
			.isInstanceOfSatisfying(ApiException.class, ex -> {
				assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
				assertThat(ex.getCode()).isEqualTo("OTP_INVALID");
			});
		assertThat(token.getAttempts()).isEqualTo(1);
		assertThat(token.isConsumed()).isFalse();
		assertThat(user.isPhoneVerified()).isFalse();
		verify(otpTokens).save(token);
	}

	@Test
	void verifyOtpBurnsTheTokenAfterTooManyWrongAttempts() {
		User user = applicant(false);
		OtpToken token = token("482913", TestFixtures.CLOCK.instant().plusSeconds(120), false, 4); // limit is 5
		stubUserAndToken(user, token);

		assertThatThrownBy(() -> service.verifyOtp(new OtpVerifyRequest("driver@example.com", "000000")))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("OTP_TOO_MANY_ATTEMPTS"));
		assertThat(token.isConsumed()).isTrue();
	}

	@Test
	void verifyOtpRejectsAnExpiredCode() {
		User user = applicant(false);
		OtpToken token = token("482913", TestFixtures.CLOCK.instant().minusSeconds(1), false, 0);
		stubUserAndToken(user, token);

		assertThatThrownBy(() -> service.verifyOtp(new OtpVerifyRequest("driver@example.com", "482913")))
			.isInstanceOfSatisfying(ApiException.class, ex -> {
				assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
				assertThat(ex.getCode()).isEqualTo("OTP_EXPIRED");
			});
		assertThat(user.isPhoneVerified()).isFalse();
	}

	@Test
	void verifyOtpRejectsAConsumedCodeEvenIfTheDigitsAreRight() {
		User user = applicant(false);
		OtpToken token = token("482913", TestFixtures.CLOCK.instant().plusSeconds(120), true, 0);
		stubUserAndToken(user, token);

		assertThatThrownBy(() -> service.verifyOtp(new OtpVerifyRequest("driver@example.com", "482913")))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("OTP_ALREADY_USED"));
		assertThat(user.isPhoneVerified()).isFalse();
	}

	@Test
	void verifyOtpForAnUnknownAccountLooksLikeAnInvalidCode() {
		when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.verifyOtp(new OtpVerifyRequest("nobody@example.com", "123456")))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("OTP_INVALID"));
	}

	@Test
	void hashedOtpsAreVerifiedWithoutStoringThePlainCode() {
		TakeoffProperties hashed = TestFixtures.properties(true, false);
		OtpCodec codec = new OtpCodec(hashed, TestFixtures.environment("prod"));
		AuthService hashedService = new AuthService(users, otpTokens, encoder, jwtService, producer, codec,
				TransactionOperations.withoutTransaction(), hashed, TestFixtures.CLOCK);

		User user = applicant(false);
		String stored = codec.encode(42L, "482913");
		assertThat(stored).isNotEqualTo("482913").hasSize(64);
		stubUserAndToken(user, token(stored, TestFixtures.CLOCK.instant().plusSeconds(60), false, 0));
		when(jwtService.generateToken(user)).thenReturn("jwt");

		assertThat(hashedService.verifyOtp(new OtpVerifyRequest("driver@example.com", "482913")).accessToken())
			.isEqualTo("jwt");
		assertThatThrownBy(() -> hashedService.verifyOtp(new OtpVerifyRequest("driver@example.com", "111111")))
			.isInstanceOf(ApiException.class);
	}

	// ---------------------------------------------------------------- login

	@Test
	void loginSucceedsForAVerifiedApplicant() {
		User user = applicant(true);
		when(users.findByEmail("driver@example.com")).thenReturn(Optional.of(user));
		when(jwtService.generateToken(user)).thenReturn("jwt");

		JwtResponse response = service.login(new LoginRequest("Driver@Example.com", TestFixtures.COMPLIANT_PASSWORD));

		assertThat(response.accessToken()).isEqualTo("jwt");
		assertThat(response.user().email()).isEqualTo("driver@example.com");
	}

	@Test
	void loginGivesTheSameGenericErrorForAWrongPasswordAndAnUnknownEmail() {
		when(users.findByEmail("driver@example.com")).thenReturn(Optional.of(applicant(true)));
		when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

		ApiException wrongPassword = catchApiException(() -> service.login(new LoginRequest("driver@example.com", "Wrong#Password12345")));
		ApiException unknownEmail = catchApiException(() -> service.login(new LoginRequest("nobody@example.com", "Wrong#Password12345")));

		assertThat(wrongPassword.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(wrongPassword.getCode()).isEqualTo("INVALID_CREDENTIALS");
		assertThat(unknownEmail.getStatus()).isEqualTo(wrongPassword.getStatus());
		assertThat(unknownEmail.getCode()).isEqualTo(wrongPassword.getCode());
		assertThat(unknownEmail.getMessage()).isEqualTo(wrongPassword.getMessage());
	}

	@Test
	void loginRequiresAVerifiedPhoneForApplicants() {
		when(users.findByEmail("driver@example.com")).thenReturn(Optional.of(applicant(false)));

		ApiException ex = catchApiException(
				() -> service.login(new LoginRequest("driver@example.com", TestFixtures.COMPLIANT_PASSWORD)));

		assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(ex.getCode()).isEqualTo("PHONE_NOT_VERIFIED");
		verify(jwtService, never()).generateToken(any());
	}

	@Test
	void loginRejectsADisabledAccountWithTheGenericError() {
		User user = applicant(true);
		user.setEnabled(false);
		when(users.findByEmail("driver@example.com")).thenReturn(Optional.of(user));

		ApiException ex = catchApiException(
				() -> service.login(new LoginRequest("driver@example.com", TestFixtures.COMPLIANT_PASSWORD)));
		assertThat(ex.getCode()).isEqualTo("INVALID_CREDENTIALS");
	}

	@Test
	void adminsDoNotNeedPhoneVerification() {
		User admin = new User("Admin", "admin@takeoff.test", "+15550100", encoder.encode(TestFixtures.COMPLIANT_PASSWORD),
				Role.LOGISTICS_ADMIN);
		ReflectionTestUtils.setField(admin, "id", 1L);
		when(users.findByEmail("admin@takeoff.test")).thenReturn(Optional.of(admin));
		when(jwtService.generateToken(admin)).thenReturn("admin.jwt");

		assertThat(service.login(new LoginRequest("admin@takeoff.test", TestFixtures.COMPLIANT_PASSWORD)).user().role())
			.isEqualTo(Role.LOGISTICS_ADMIN);
	}

	// ---------------------------------------------------------------- resend

	@Test
	void resendPublishesANewOtpEventOnceTheCooldownHasPassed() {
		User user = applicant(false);
		when(users.findByEmail("driver@example.com")).thenReturn(Optional.of(user));
		when(otpTokens.findFirstByUserIdOrderByIdDesc(42L)).thenReturn(
				Optional.of(token("482913", TestFixtures.CLOCK.instant().plusSeconds(120), false, 0))); // created 60s ago

		service.resendOtp(new ResendOtpRequest("driver@example.com"));

		verify(producer).publishOtpGenerate(42L, PHONE);
	}

	@Test
	void resendIsRateLimitedDuringTheCooldown() {
		User user = applicant(false);
		when(users.findByEmail("driver@example.com")).thenReturn(Optional.of(user));
		OtpToken justIssued = new OtpToken(42L, "482913", TestFixtures.CLOCK.instant().plusSeconds(300),
				TestFixtures.CLOCK.instant().minusSeconds(5));
		when(otpTokens.findFirstByUserIdOrderByIdDesc(42L)).thenReturn(Optional.of(justIssued));

		ApiException ex = catchApiException(() -> service.resendOtp(new ResendOtpRequest("driver@example.com")));

		assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(ex.getCode()).isEqualTo("OTP_RESEND_TOO_SOON");
		verify(producer, never()).publishOtpGenerate(anyLong(), anyString());
	}

	@Test
	void resendForAnUnknownAccountRevealsNothingAndPublishesNothing() {
		when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

		assertThat(service.resendOtp(new ResendOtpRequest("nobody@example.com")).otpDispatched()).isTrue();
		verify(producer, never()).publishOtpGenerate(anyLong(), anyString());
	}

	@Test
	void resendIsCappedPerHourBecauseRealSmsCostsMoney() {
		User user = applicant(false);
		when(users.findByEmail("driver@example.com")).thenReturn(Optional.of(user));
		when(otpTokens.findFirstByUserIdOrderByIdDesc(42L)).thenReturn(
				Optional.of(token("482913", TestFixtures.CLOCK.instant().plusSeconds(120), false, 0))); // cooldown passed
		when(otpTokens.countByUserIdAndCreatedAtAfter(eq(42L), any(Instant.class))).thenReturn(5L); // limit is 5

		ApiException ex = catchApiException(() -> service.resendOtp(new ResendOtpRequest("driver@example.com")));

		assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(ex.getCode()).isEqualTo("OTP_SEND_LIMIT");
		verify(producer, never()).publishOtpGenerate(anyLong(), anyString());
	}

	@Test
	void resendIsStillAllowedJustUnderTheHourlyCap() {
		User user = applicant(false);
		when(users.findByEmail("driver@example.com")).thenReturn(Optional.of(user));
		when(otpTokens.findFirstByUserIdOrderByIdDesc(42L)).thenReturn(
				Optional.of(token("482913", TestFixtures.CLOCK.instant().plusSeconds(120), false, 0)));
		when(otpTokens.countByUserIdAndCreatedAtAfter(eq(42L), any(Instant.class))).thenReturn(4L);

		service.resendOtp(new ResendOtpRequest("driver@example.com"));

		verify(producer).publishOtpGenerate(42L, PHONE);
	}

	private static ApiException catchApiException(Runnable action) {
		try {
			action.run();
		}
		catch (ApiException ex) {
			return ex;
		}
		throw new AssertionError("Expected an ApiException");
	}
}
