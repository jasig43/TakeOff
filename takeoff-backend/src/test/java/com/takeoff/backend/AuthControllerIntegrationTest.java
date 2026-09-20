package com.takeoff.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.takeoff.backend.config.TakeoffProperties;
import com.takeoff.backend.model.OtpToken;
import com.takeoff.backend.model.Role;
import com.takeoff.backend.model.User;
import com.takeoff.backend.repository.OtpTokenRepository;
import com.takeoff.backend.repository.UserRepository;
import com.takeoff.backend.security.JwtService;
import com.takeoff.backend.service.OtpConsumerListener;

import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end tests over the real Spring context: HTTP -> security chain -> services -> H2 (with the real
 * Flyway migration). RabbitMQ is the only stand-in: the {@link RabbitTemplate} is mocked and the message it
 * would have sent is handed straight to the real {@link OtpConsumerListener}, exactly as the broker would.
 */
@TakeoffIntegrationTest
class AuthControllerIntegrationTest {

	private static final String EXCHANGE = "takeoff.exchange";
	private static final String ROUTING_KEY = "otp.routing.key";
	private static final String PASSWORD = "Sturdy#Password2026";
	private static final String TEST_PHONE = "+15550199";
	private static final String ADMIN_EMAIL = "admin@takeoff.test";
	private static final String ADMIN_PASSWORD = "Test-Admin-Password#2026";

	@Autowired
	MockMvc mvc;
	@Autowired
	ObjectMapper objectMapper;
	@Autowired
	UserRepository users;
	@Autowired
	OtpTokenRepository otpTokens;
	@Autowired
	OtpConsumerListener otpConsumer;
	@Autowired
	TakeoffProperties properties;
	@Autowired
	Environment environment;

	@MockitoBean
	RabbitTemplate rabbitTemplate;

	@BeforeEach
	void removeApplicants() {
		otpTokens.deleteAll();
		users.findAll().stream().filter(user -> user.getRole() == Role.APPLICANT_DRIVER).forEach(users::delete);
	}

	// ------------------------------------------------------------------ helpers

	private Map<String, Object> signUpBody(String email, String phone, String password) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("fullName", "Test Driver");
		body.put("email", email);
		body.put("phoneNumber", phone);
		body.put("password", password);
		body.put("termsAccepted", true);
		return body;
	}

	private ResultActions postJson(String path, Object body) throws Exception {
		return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
	}

	private ResultActions register(String email, String phone, String password) throws Exception {
		return postJson("/api/v1/auth/register", signUpBody(email, phone, password));
	}

	/** Hands the most recently published OTP event to the real consumer, as RabbitMQ would. */
	private void deliverPublishedOtpEvent() {
		ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
		verify(rabbitTemplate, atLeastOnce()).send(eq(EXCHANGE), eq(ROUTING_KEY), message.capture());
		otpConsumer.onMessage(message.getValue());
	}

	private String currentOtp(String email) {
		User user = users.findByEmail(email).orElseThrow();
		return otpTokens.findFirstByUserIdOrderByIdDesc(user.getId()).orElseThrow().getCode();
	}

	private ResultActions submitOtp(String identifier, String otp) throws Exception {
		return postJson("/api/v1/auth/verify-otp", Map.of("identifier", identifier, "otp", otp));
	}

	/** Registers, runs the OTP through the consumer, verifies, and returns the JWT. */
	private String registerAndVerify(String email, String phone) throws Exception {
		register(email, phone, PASSWORD).andExpect(status().isCreated());
		deliverPublishedOtpEvent();
		String body = submitOtp(email, currentOtp(email)).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

	private String loginAsAdmin() throws Exception {
		String body = postJson("/api/v1/auth/login", Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	// ------------------------------------------------------------------ registration + password policy

	@Test
	void registrationWithACompliantPasswordReturns201AndPublishesAnOtpGenerateEvent() throws Exception {
		String response = register("driver1@example.com", "+15550111", PASSWORD).andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("driver1@example.com"))
			.andExpect(jsonPath("$.otpDispatched").value(true))
			.andExpect(jsonPath("$.otpExpiresInSeconds").value(300))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.otp").doesNotExist())
			.andReturn().getResponse().getContentAsString();
		assertThat(response).doesNotContain(PASSWORD).doesNotContain("123456");

		User saved = users.findByEmail("driver1@example.com").orElseThrow();
		assertThat(saved.getRole()).isEqualTo(Role.APPLICANT_DRIVER);
		assertThat(saved.isPhoneVerified()).isFalse();
		assertThat(saved.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);

		ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
		verify(rabbitTemplate).send(eq(EXCHANGE), eq(ROUTING_KEY), message.capture());
		String payload = new String(message.getValue().getBody(), StandardCharsets.UTF_8);
		assertThat((String) JsonPath.read(payload, "$.eventType")).isEqualTo("OTP_GENERATE");
		assertThat(((Number) JsonPath.read(payload, "$.userId")).longValue()).isEqualTo(saved.getId());
		assertThat((String) JsonPath.read(payload, "$.phoneNumber")).isEqualTo("+15550111");
		assertThat((String) JsonPath.read(payload, "$.requestedAt")).isNotBlank();
		assertThat(payload).doesNotContain(PASSWORD).doesNotContain("password").doesNotContain("otp\"");
	}

	@Test
	void aPasswordShorterThan15CharactersIsRejectedWith400() throws Exception {
		register("short@example.com", "+15550112", "Short#Pass1234") // 14 characters
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')].message", hasItem(containsString("at least 15"))));
		assertThat(users.existsByEmail("short@example.com")).isFalse();
	}

	@Test
	void aPasswordWithoutAnUppercaseLetterIsRejectedWith400() throws Exception {
		register("lower@example.com", "+15550113", "no-uppercase-here!!")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')].message", hasItem(containsString("uppercase"))));
	}

	@Test
	void aPasswordWithoutASpecialCharacterIsRejectedWith400() throws Exception {
		register("plain@example.com", "+15550114", "NoSpecialCharsAtAll1")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')].message", hasItem(containsString("special character"))));
	}

	@Test
	void invalidEmailPhoneAndMissingTermsAreReportedPerField() throws Exception {
		Map<String, Object> body = signUpBody("not-an-email", "12345", PASSWORD);
		body.put("termsAccepted", false);

		postJson("/api/v1/auth/register", body).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("email")))
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("phoneNumber")))
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("termsAccepted")));
	}

	@Test
	void aDuplicateEmailIsRejectedWith409() throws Exception {
		register("dup@example.com", "+15550115", PASSWORD).andExpect(status().isCreated());

		register("dup@example.com", "+15550116", PASSWORD).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
			.andExpect(jsonPath("$.status").value(409));
		// Email matching is case-insensitive.
		register("DUP@Example.com", "+15550117", PASSWORD).andExpect(status().isConflict());
	}

	@Test
	void aDuplicatePhoneNumberIsRejectedWith409() throws Exception {
		register("first@example.com", "+15550118", PASSWORD).andExpect(status().isCreated());

		register("second@example.com", "+15550118", PASSWORD).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PHONE_ALREADY_REGISTERED"));
	}

	@Test
	void publicRegistrationCanNeverCreateAnAdministrator() throws Exception {
		Map<String, Object> body = signUpBody("sneaky@example.com", "+15550119", PASSWORD);
		body.put("role", "LOGISTICS_ADMIN");
		body.put("phoneVerified", true);
		body.put("enabled", true);

		postJson("/api/v1/auth/register", body).andExpect(status().isCreated());

		User saved = users.findByEmail("sneaky@example.com").orElseThrow();
		assertThat(saved.getRole()).isEqualTo(Role.APPLICANT_DRIVER);
		assertThat(saved.isPhoneVerified()).isFalse();
	}

	@Test
	void registrationSucceedsButFlagsTheOtpAsNotDispatchedWhenRabbitMqIsDown() throws Exception {
		doThrow(new AmqpConnectException(new RuntimeException("broker down"))).when(rabbitTemplate)
			.send(any(String.class), any(String.class), any(Message.class));

		register("offline@example.com", "+15550120", PASSWORD).andExpect(status().isCreated())
			.andExpect(jsonPath("$.otpDispatched").value(false))
			.andExpect(jsonPath("$.message").value(containsString("Resend code")));
		assertThat(users.existsByEmail("offline@example.com")).isTrue();
	}

	@Test
	void malformedJsonGetsTheStandardErrorBodyNotAStackTrace() throws Exception {
		String body = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{ nope"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"))
			.andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
			.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("Exception").doesNotContain("at com.");
	}

	// ------------------------------------------------------------------ OTP flow

	@Test
	void theTestPhoneNumberReceivesTheFixedOtp123456AndCanVerifyWithIt() throws Exception {
		register("evaluator@example.com", TEST_PHONE, PASSWORD).andExpect(status().isCreated());
		deliverPublishedOtpEvent();

		assertThat(currentOtp("evaluator@example.com")).isEqualTo("123456");

		submitOtp("evaluator@example.com", "123456").andExpect(status().isOk())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.user.phoneVerified").value(true))
			.andExpect(jsonPath("$.user.role").value("APPLICANT_DRIVER"))
			.andExpect(jsonPath("$.user.passwordHash").doesNotExist());
		assertThat(users.findByEmail("evaluator@example.com").orElseThrow().isPhoneVerified()).isTrue();
	}

	@Test
	void otherPhoneNumbersGetARandomCodeNotTheFixedOne() throws Exception {
		register("random@example.com", "+15550121", PASSWORD).andExpect(status().isCreated());
		deliverPublishedOtpEvent();

		assertThat(currentOtp("random@example.com")).matches("\\d{6}");
	}

	@Test
	void verificationReturnsAJwtCarryingTheRoleAuthority(@Autowired JwtService jwtService) throws Exception {
		String token = registerAndVerify("jwt@example.com", "+15550122");

		var jwt = jwtService.parse(token);
		assertThat(jwt.getClaimAsStringList("authorities")).containsExactly("ROLE_APPLICANT_DRIVER");
		assertThat(jwt.getClaimAsString("email")).isEqualTo("jwt@example.com");
		assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
	}

	@Test
	void aWrongOtpIsRejectedWith400AndDoesNotVerifyThePhone() throws Exception {
		register("wrong@example.com", "+15550123", PASSWORD).andExpect(status().isCreated());
		deliverPublishedOtpEvent();
		String realCode = currentOtp("wrong@example.com");
		String wrongCode = realCode.equals("000000") ? "000001" : "000000";

		submitOtp("wrong@example.com", wrongCode).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("OTP_INVALID"))
			.andExpect(jsonPath("$.accessToken").doesNotExist());
		assertThat(users.findByEmail("wrong@example.com").orElseThrow().isPhoneVerified()).isFalse();
	}

	@Test
	void aMalformedOtpIsRejectedByValidation() throws Exception {
		submitOtp("wrong@example.com", "12ab56").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		submitOtp("wrong@example.com", "12345").andExpect(status().isBadRequest());
	}

	@Test
	void anExpiredOtpIsRejectedWith400() throws Exception {
		register("expired@example.com", "+15550124", PASSWORD).andExpect(status().isCreated());
		deliverPublishedOtpEvent();
		OtpToken token = otpTokens.findFirstByUserIdOrderByIdDesc(users.findByEmail("expired@example.com").orElseThrow().getId())
			.orElseThrow();
		String code = token.getCode();
		token.setExpiresAt(Instant.now().minusSeconds(5));
		otpTokens.save(token);

		submitOtp("expired@example.com", code).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
		assertThat(users.findByEmail("expired@example.com").orElseThrow().isPhoneVerified()).isFalse();
	}

	@Test
	void aConsumedOtpCannotBeUsedASecondTime() throws Exception {
		register("reuse@example.com", TEST_PHONE, PASSWORD).andExpect(status().isCreated());
		deliverPublishedOtpEvent();
		submitOtp("reuse@example.com", "123456").andExpect(status().isOk());

		submitOtp("reuse@example.com", "123456").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("OTP_ALREADY_USED"));
	}

	@Test
	void aNewOtpInvalidatesThePreviousOne() throws Exception {
		register("replace@example.com", "+15550125", PASSWORD).andExpect(status().isCreated());
		deliverPublishedOtpEvent();
		String first = currentOtp("replace@example.com");
		deliverPublishedOtpEvent(); // a second issue for the same user (e.g. after "resend")
		String second = currentOtp("replace@example.com");

		if (!first.equals(second)) {
			submitOtp("replace@example.com", first).andExpect(status().isBadRequest());
		}
		submitOtp("replace@example.com", second).andExpect(status().isOk());
	}

	@Test
	void tooManyWrongGuessesBurnTheOtp() throws Exception {
		register("brute@example.com", TEST_PHONE, PASSWORD).andExpect(status().isCreated());
		deliverPublishedOtpEvent();

		for (int i = 0; i < 4; i++) {
			submitOtp("brute@example.com", "000000").andExpect(jsonPath("$.code").value("OTP_INVALID"));
		}
		submitOtp("brute@example.com", "000000").andExpect(jsonPath("$.code").value("OTP_TOO_MANY_ATTEMPTS"));
		// Even the correct code no longer works: the attacker has to request a new one.
		submitOtp("brute@example.com", "123456").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("OTP_ALREADY_USED"));
	}

	@Test
	void resendPublishesAnotherOtpEventAndUnknownAccountsLookIdentical() throws Exception {
		register("resend@example.com", "+15550126", PASSWORD).andExpect(status().isCreated());

		postJson("/api/v1/auth/resend-otp", Map.of("identifier", "resend@example.com")).andExpect(status().isOk())
			.andExpect(jsonPath("$.otpDispatched").value(true));
		verify(rabbitTemplate, times(2)).send(eq(EXCHANGE), eq(ROUTING_KEY), any(Message.class));

		postJson("/api/v1/auth/resend-otp", Map.of("identifier", "nobody@example.com")).andExpect(status().isOk())
			.andExpect(jsonPath("$.otpDispatched").value(true));
		verify(rabbitTemplate, times(2)).send(eq(EXCHANGE), eq(ROUTING_KEY), any(Message.class)); // nothing new sent
	}

	@Test
	void resendReturns503WhenRabbitMqIsDown() throws Exception {
		register("resend2@example.com", "+15550127", PASSWORD).andExpect(status().isCreated());
		doThrow(new AmqpConnectException(new RuntimeException("broker down"))).when(rabbitTemplate)
			.send(any(String.class), any(String.class), any(Message.class));

		postJson("/api/v1/auth/resend-otp", Map.of("identifier", "resend2@example.com"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("OTP_DISPATCH_FAILED"));
	}

	// ------------------------------------------------------------------ login

	@Test
	void loginIsRefusedUntilThePhoneIsVerifiedThenSucceeds() throws Exception {
		register("login@example.com", TEST_PHONE, PASSWORD).andExpect(status().isCreated());

		postJson("/api/v1/auth/login", Map.of("email", "login@example.com", "password", PASSWORD))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("PHONE_NOT_VERIFIED"));

		deliverPublishedOtpEvent();
		submitOtp("login@example.com", "123456").andExpect(status().isOk());

		postJson("/api/v1/auth/login", Map.of("email", "LOGIN@example.com", "password", PASSWORD))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.expiresInSeconds").value(900));
	}

	@Test
	void invalidCredentialsGetAGenericMessage() throws Exception {
		registerAndVerify("creds@example.com", "+15550128");

		String wrongPassword = postJson("/api/v1/auth/login", Map.of("email", "creds@example.com", "password", "Wrong#Password2026"))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
			.andReturn().getResponse().getContentAsString();
		String unknownEmail = postJson("/api/v1/auth/login", Map.of("email", "ghost@example.com", "password", "Wrong#Password2026"))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
			.andReturn().getResponse().getContentAsString();

		assertThat((String) JsonPath.read(wrongPassword, "$.message")).isEqualTo(JsonPath.read(unknownEmail, "$.message"));
	}

	// ------------------------------------------------------------------ RBAC

	@Test
	void anApplicantCanReadTheirOwnProfile() throws Exception {
		String token = registerAndVerify("profile@example.com", "+15550129");

		mvc.perform(get("/api/v1/drivers/profile").header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("profile@example.com"))
			.andExpect(jsonPath("$.phoneVerified").value(true))
			.andExpect(jsonPath("$.role").value("APPLICANT_DRIVER"))
			.andExpect(jsonPath("$.passwordHash").doesNotExist())
			.andExpect(jsonPath("$.password").doesNotExist());
	}

	@Test
	void anApplicantIsForbiddenFromAdminEndpoints() throws Exception {
		String token = registerAndVerify("nosy@example.com", "+15550130");

		mvc.perform(get("/api/v1/admin/health").header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"))
			.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void anAdministratorCanReachAdminEndpointsButNotApplicantOnes() throws Exception {
		String adminToken = loginAsAdmin();

		mvc.perform(get("/api/v1/admin/health").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.role").value("LOGISTICS_ADMIN"));

		mvc.perform(get("/api/v1/drivers/profile").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
			.andExpect(status().isForbidden());
	}

	@Test
	void requestsWithoutATokenAreRejectedWith401AndAJsonBody() throws Exception {
		mvc.perform(get("/api/v1/drivers/profile")).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.path").value("/api/v1/drivers/profile"));
		mvc.perform(get("/api/v1/admin/health")).andExpect(status().isUnauthorized());
	}

	@Test
	void invalidTamperedAndExpiredTokensAreRejectedWith401() throws Exception {
		String valid = registerAndVerify("tamper@example.com", "+15550131");
		String[] parts = valid.split("\\.");
		char original = parts[1].charAt(10);
		String tampered = parts[0] + "." + parts[1].substring(0, 10) + (original == 'A' ? 'B' : 'A') + parts[1].substring(11)
				+ "." + parts[2];
		Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(3)), ZoneOffset.UTC);
		String expired = new JwtService(properties, environment, past)
			.generateToken(users.findByEmail("tamper@example.com").orElseThrow());

		for (String bad : new String[] { "garbage", tampered, expired }) {
			mvc.perform(get("/api/v1/drivers/profile").header(HttpHeaders.AUTHORIZATION, bearer(bad)))
				.andExpect(status().isUnauthorized());
		}
		mvc.perform(get("/api/v1/drivers/profile").header(HttpHeaders.AUTHORIZATION, valid)) // missing "Bearer "
			.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/drivers/profile").header(HttpHeaders.AUTHORIZATION, bearer(valid)))
			.andExpect(status().isOk());
	}

	@Test
	void aDisabledAccountsExistingTokenStopsWorkingImmediately() throws Exception {
		String token = registerAndVerify("fired@example.com", "+15550132");
		User user = users.findByEmail("fired@example.com").orElseThrow();
		user.setEnabled(false);
		users.save(user);

		mvc.perform(get("/api/v1/drivers/profile").header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void corsAllowsTheConfiguredFrontendOriginOnly() throws Exception {
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/auth/login")
			.header(HttpHeaders.ORIGIN, "http://localhost:5173")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
			.andExpect(status().isOk())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
				.string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));

		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/auth/login")
			.header(HttpHeaders.ORIGIN, "https://evil.example")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
			.andExpect(status().isForbidden())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
				.doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}
}
