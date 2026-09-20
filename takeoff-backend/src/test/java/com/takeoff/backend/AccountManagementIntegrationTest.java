package com.takeoff.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.takeoff.backend.dto.UserAdminDtos.AdminUserDto;
import com.takeoff.backend.dto.UserAdminDtos.IssuedCredentialDto;
import com.takeoff.backend.model.Role;
import com.takeoff.backend.model.User;
import com.takeoff.backend.repository.UserRepository;
import com.takeoff.backend.validation.PasswordPolicy;

import tools.jackson.databind.ObjectMapper;

/**
 * Administrators creating accounts with a temporary password, and assigning roles: the whole path over the real
 * security chain, including what the new person can and cannot do before they choose their own password.
 * It uses its own throw-away accounts (email prefix "acct-") so the seeded admin is never touched.
 */
@TakeoffIntegrationTest
class AccountManagementIntegrationTest {

	private static final String ADMIN_EMAIL = "acct-admin@takeoff.test";
	private static final String DRIVER_EMAIL = "acct-driver@takeoff.test";
	private static final String PASSWORD = "Managed-Account#Password1";
	private static final String CHOSEN = "My-Very-Own#Password2027";
	private static final String USERS = "/api/v1/admin/users";
	private static final AtomicInteger SEQUENCE = new AtomicInteger(100);

	@Autowired
	MockMvc mvc;
	@Autowired
	ObjectMapper objectMapper;
	@Autowired
	UserRepository users;
	@Autowired
	PasswordEncoder encoder;

	@MockitoBean
	RabbitTemplate rabbitTemplate;

	@BeforeEach
	void createAccounts() {
		removeAccounts();
		saveUser("Account Admin", ADMIN_EMAIL, "+15550781", Role.LOGISTICS_ADMIN);
		saveUser("Account Driver", DRIVER_EMAIL, "+15550782", Role.APPLICANT_DRIVER);
	}

	@AfterEach
	void removeAccounts() {
		users.findAll().stream().filter(u -> u.getEmail().startsWith("acct-")).forEach(users::delete);
	}

	// ------------------------------------------------------------------ helpers

	private User saveUser(String name, String email, String phone, Role role) {
		User user = new User(name, email, phone, encoder.encode(PASSWORD), role);
		user.setPhoneVerified(true);
		return users.save(user);
	}

	private ResultActions send(AbstractMockHttpServletRequestBuilder<?> request, String token, Object body) throws Exception {
		if (token != null) {
			request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
		}
		return mvc.perform(request);
	}

	private ResultActions login(String email, String password) throws Exception {
		return send(post("/api/v1/auth/login"), null, Map.of("email", email, "password", password));
	}

	private String token(String email, String password) throws Exception {
		String body = login(email, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

	private String adminToken() throws Exception {
		return token(ADMIN_EMAIL, PASSWORD);
	}

	private static Map<String, Object> newAccount(String name, String email, String phone, String role) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("fullName", name);
		body.put("email", email);
		body.put("phoneNumber", phone);
		body.put("role", role);
		return body;
	}

	private static String uniquePhone() {
		return "+2637790" + (1000 + SEQUENCE.incrementAndGet());
	}

	private static String uniqueEmail() {
		return "acct-new-" + SEQUENCE.incrementAndGet() + "@takeoff.test";
	}

	/** Creates an account through the API as the admin and returns what the admin was shown. */
	private IssuedCredentialDto create(String role) throws Exception {
		String body = send(post(USERS), adminToken(), newAccount("Created Person", uniqueEmail(), uniquePhone(), role))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return objectMapper.readValue(body, IssuedCredentialDto.class);
	}

	private ResultActions changePassword(String token, String current, String next) throws Exception {
		return send(put("/api/v1/account/password"), token, Map.of("currentPassword", current, "newPassword", next));
	}

	// ------------------------------------------------------------------ creating an account

	@Test
	void anAdminCreatesAnAccountAndIsShownTheTemporaryPasswordOnce() throws Exception {
		String email = uniqueEmail();
		String body = send(post(USERS), adminToken(), newAccount("  Tendai Moyo ", email.toUpperCase(), uniquePhone(), "APPLICANT_DRIVER"))
			.andExpect(status().isCreated())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
			.andExpect(jsonPath("$.user.fullName").value("Tendai Moyo"))
			.andExpect(jsonPath("$.user.email").value(email)) // trimmed and lower-cased
			.andExpect(jsonPath("$.user.role").value("APPLICANT_DRIVER"))
			.andExpect(jsonPath("$.user.phoneVerified").value(true))
			.andExpect(jsonPath("$.user.mustChangePassword").value(true))
			.andExpect(jsonPath("$.user.passwordHash").doesNotExist())
			.andReturn().getResponse().getContentAsString();

		String temporary = JsonPath.read(body, "$.temporaryPassword");
		assertThat(PasswordPolicy.violations(temporary)).isEmpty();
		Instant expires = Instant.parse(JsonPath.read(body, "$.temporaryPasswordExpiresAt"));
		assertThat(expires).isBetween(Instant.now().plus(Duration.ofHours(71)), Instant.now().plus(Duration.ofHours(73)));

		// stored only as a hash
		User stored = users.findByEmail(email).orElseThrow();
		assertThat(stored.getPasswordHash()).isNotEqualTo(temporary);
		assertThat(encoder.matches(temporary, stored.getPasswordHash())).isTrue();

		// and never shown again: the user list does not contain it
		send(get(USERS + "?q=" + email), adminToken(), null).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].email").value(email))
			.andExpect(content().string(not(containsString(temporary))))
			.andExpect(content().string(not(containsString("temporaryPassword\""))));
	}

	@Test
	void theTemporaryPasswordSignsThePersonInButLetsThemDoNothingElseUntilTheyChooseTheirOwn() throws Exception {
		IssuedCredentialDto issued = create("APPLICANT_DRIVER");
		String email = issued.user().email();

		// 1. signing in works and says the password must be changed
		String loginBody = login(email, issued.temporaryPassword()).andExpect(status().isOk())
			.andExpect(jsonPath("$.user.mustChangePassword").value(true))
			.andExpect(jsonPath("$.user.role").value("APPLICANT_DRIVER"))
			.andReturn().getResponse().getContentAsString();
		String token = JsonPath.read(loginBody, "$.accessToken");

		// 2. every role-protected route refuses them, with a code the client can act on
		send(get("/api/v1/drivers/application"), token, null).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
		send(get("/api/v1/drivers/profile"), token, null).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
		send(get(USERS), token, null).andExpect(status().isForbidden());

		// 3. choosing their own password is allowed, and the same session then works
		changePassword(token, issued.temporaryPassword(), CHOSEN).andExpect(status().isOk())
			.andExpect(jsonPath("$.mustChangePassword").value(false));
		send(get("/api/v1/drivers/application"), token, null).andExpect(status().isOk());

		// 4. the temporary password is dead; the chosen one works and no longer forces a change
		login(email, issued.temporaryPassword()).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		login(email, CHOSEN).andExpect(status().isOk()).andExpect(jsonPath("$.user.mustChangePassword").value(false));
		assertThat(users.findByEmail(email).orElseThrow().getTemporaryPasswordExpiresAt()).isNull();
	}

	@Test
	void aNewAdministratorAlsoMustChooseAPasswordBeforeUsingTheAdminArea() throws Exception {
		IssuedCredentialDto issued = create("LOGISTICS_ADMIN");
		String token = token(issued.user().email(), issued.temporaryPassword());

		send(get(USERS), token, null).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
		send(get("/api/v1/admin/summary"), token, null).andExpect(status().isForbidden());

		changePassword(token, issued.temporaryPassword(), CHOSEN).andExpect(status().isOk());
		send(get(USERS), token, null).andExpect(status().isOk());
		send(get("/api/v1/admin/summary"), token, null).andExpect(status().isOk());
	}

	@Test
	void theNewPasswordMustStillDifferFromTheTemporaryOneAndMeetThePolicy() throws Exception {
		IssuedCredentialDto issued = create("APPLICANT_DRIVER");
		String token = token(issued.user().email(), issued.temporaryPassword());

		changePassword(token, issued.temporaryPassword(), issued.temporaryPassword()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'newPassword')].message", hasItem(containsString("different"))));
		changePassword(token, issued.temporaryPassword(), "weak").andExpect(status().isBadRequest());
		changePassword(token, "Not-The-Temporary#Password9", CHOSEN).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'currentPassword')].message", hasItem(containsString("not correct"))));

		// still stuck on the temporary password
		send(get("/api/v1/drivers/application"), token, null).andExpect(status().isForbidden());
	}

	@Test
	void aTemporaryPasswordExpires_andAnExpiredOneIsNoLongerASession() throws Exception {
		IssuedCredentialDto issued = create("APPLICANT_DRIVER");
		String email = issued.user().email();
		String token = token(email, issued.temporaryPassword()); // signed in while it was still valid

		User user = users.findByEmail(email).orElseThrow();
		user.issueTemporaryPassword(user.getPasswordHash(), Instant.now().minusSeconds(60)); // time runs out
		users.save(user);

		login(email, issued.temporaryPassword()).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("TEMPORARY_PASSWORD_EXPIRED"));
		changePassword(token, issued.temporaryPassword(), CHOSEN).andExpect(status().isUnauthorized()); // the old token is dead too
		// a wrong password on an expired account does not reveal that it is expired
		login(email, "Wrong-Password#12345").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	// ------------------------------------------------------------------ resetting a temporary password

	@Test
	void anAdminCanIssueANewTemporaryPassword_whichReplacesTheOldOneAndForcesAChange() throws Exception {
		User driver = users.findByEmail(DRIVER_EMAIL).orElseThrow();

		String body = send(post(USERS + "/" + driver.getId() + "/temporary-password"), adminToken(), null)
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
			.andExpect(jsonPath("$.user.mustChangePassword").value(true))
			.andReturn().getResponse().getContentAsString();
		String fresh = JsonPath.read(body, "$.temporaryPassword");
		assertThat(PasswordPolicy.violations(fresh)).isEmpty();

		login(DRIVER_EMAIL, PASSWORD).andExpect(status().isUnauthorized()); // the old password no longer works
		login(DRIVER_EMAIL, fresh).andExpect(status().isOk()).andExpect(jsonPath("$.user.mustChangePassword").value(true));
	}

	@Test
	void anAdminCannotResetTheirOwnPassword_andUnknownAccountsAreNotFound() throws Exception {
		User admin = users.findByEmail(ADMIN_EMAIL).orElseThrow();
		String token = adminToken();

		send(post(USERS + "/" + admin.getId() + "/temporary-password"), token, null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CANNOT_RESET_OWN_PASSWORD"));
		send(post(USERS + "/999999/temporary-password"), token, null).andExpect(status().isNotFound());
		login(ADMIN_EMAIL, PASSWORD).andExpect(status().isOk()); // untouched
	}

	// ------------------------------------------------------------------ assigning roles

	@Test
	void assigningARoleTakesEffectImmediatelyEvenForAnAlreadyIssuedToken() throws Exception {
		User driver = users.findByEmail(DRIVER_EMAIL).orElseThrow();
		String driverToken = token(DRIVER_EMAIL, PASSWORD);
		send(get("/api/v1/drivers/application"), driverToken, null).andExpect(status().isOk());
		send(get("/api/v1/admin/summary"), driverToken, null).andExpect(status().isForbidden());

		send(patch(USERS + "/" + driver.getId() + "/role"), adminToken(), Map.of("role", "LOGISTICS_ADMIN"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.role").value("LOGISTICS_ADMIN"));

		// the very same token now opens the admin area and no longer the driver area
		send(get("/api/v1/admin/summary"), driverToken, null).andExpect(status().isOk());
		send(get("/api/v1/drivers/application"), driverToken, null).andExpect(status().isForbidden());

		// and back again
		send(patch(USERS + "/" + driver.getId() + "/role"), adminToken(), Map.of("role", "APPLICANT_DRIVER"))
			.andExpect(status().isOk());
		send(get("/api/v1/admin/summary"), driverToken, null).andExpect(status().isForbidden());
		send(get("/api/v1/drivers/application"), driverToken, null).andExpect(status().isOk());
	}

	@Test
	void anAdminCannotChangeTheirOwnRole_soTheLastAdministratorCanNeverBeRemoved() throws Exception {
		User admin = users.findByEmail(ADMIN_EMAIL).orElseThrow();

		send(patch(USERS + "/" + admin.getId() + "/role"), adminToken(), Map.of("role", "APPLICANT_DRIVER"))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANNOT_CHANGE_OWN_ROLE"));

		assertThat(users.findByEmail(ADMIN_EMAIL).orElseThrow().getRole()).isEqualTo(Role.LOGISTICS_ADMIN);
	}

	@Test
	void anotherAdministratorCanDemoteAnAdministrator_andReassigningTheSameRoleChangesNothing() throws Exception {
		IssuedCredentialDto second = create("LOGISTICS_ADMIN");
		long secondId = second.user().id();

		send(patch(USERS + "/" + secondId + "/role"), adminToken(), Map.of("role", "LOGISTICS_ADMIN"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.role").value("LOGISTICS_ADMIN"));
		send(patch(USERS + "/" + secondId + "/role"), adminToken(), Map.of("role", "APPLICANT_DRIVER"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.role").value("APPLICANT_DRIVER"));
		assertThat(users.findById(secondId).orElseThrow().getRole()).isEqualTo(Role.APPLICANT_DRIVER);
	}

	@Test
	void roleAssignmentRejectsUnknownAccountsAndUnknownRoles() throws Exception {
		User driver = users.findByEmail(DRIVER_EMAIL).orElseThrow();
		String token = adminToken();

		send(patch(USERS + "/999999/role"), token, Map.of("role", "LOGISTICS_ADMIN")).andExpect(status().isNotFound());
		send(patch(USERS + "/" + driver.getId() + "/role"), token, Map.of("role", "SUPERUSER")).andExpect(status().isBadRequest());
		send(patch(USERS + "/" + driver.getId() + "/role"), token, new HashMap<String, Object>()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("role")));
		assertThat(users.findByEmail(DRIVER_EMAIL).orElseThrow().getRole()).isEqualTo(Role.APPLICANT_DRIVER);
	}

	// ------------------------------------------------------------------ validation and duplicates

	@Test
	void creatingAnAccountValidatesEveryField() throws Exception {
		String token = adminToken();

		send(post(USERS), token, newAccount("", "not-an-email", "12345", null)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("fullName")))
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("email")))
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("phoneNumber")))
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("role")));
		send(post(USERS), token, newAccount("Some One", uniqueEmail(), "+2630778657160", "APPLICANT_DRIVER"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'phoneNumber')].message", hasItem(containsString("leading 0"))));
		send(post(USERS), token, newAccount("Some One", uniqueEmail(), uniquePhone(), "SUPERUSER")).andExpect(status().isBadRequest());
	}

	@Test
	void duplicateEmailsAndPhonesAreRefused_andNothingIsCreated() throws Exception {
		String token = adminToken();
		long before = users.count();

		send(post(USERS), token, newAccount("Dup", DRIVER_EMAIL.toUpperCase(), uniquePhone(), "APPLICANT_DRIVER"))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
		send(post(USERS), token, newAccount("Dup", uniqueEmail(), "+15550782", "APPLICANT_DRIVER"))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PHONE_ALREADY_REGISTERED"));

		assertThat(users.count()).isEqualTo(before);
	}

	// ------------------------------------------------------------------ who may do this

	@Test
	void onlyAdministratorsCanManageAccounts() throws Exception {
		User driver = users.findByEmail(DRIVER_EMAIL).orElseThrow();
		String driverToken = token(DRIVER_EMAIL, PASSWORD);
		Map<String, Object> body = newAccount("Nope", uniqueEmail(), uniquePhone(), "LOGISTICS_ADMIN");
		long before = users.count();

		for (String token : new String[] { driverToken, null }) {
			int expected = token == null ? 401 : 403;
			send(get(USERS), token, null).andExpect(status().is(expected));
			send(post(USERS), token, body).andExpect(status().is(expected));
			send(patch(USERS + "/" + driver.getId() + "/role"), token, Map.of("role", "LOGISTICS_ADMIN")).andExpect(status().is(expected));
			send(post(USERS + "/" + driver.getId() + "/temporary-password"), token, null).andExpect(status().is(expected));
		}

		assertThat(users.count()).isEqualTo(before);
		assertThat(users.findByEmail(DRIVER_EMAIL).orElseThrow().getRole()).isEqualTo(Role.APPLICANT_DRIVER);
	}

	// ------------------------------------------------------------------ the list

	@Test
	void theUserListPagesAndSearchesWithoutExposingSecrets() throws Exception {
		String token = adminToken();
		IssuedCredentialDto one = create("APPLICANT_DRIVER");
		create("APPLICANT_DRIVER");

		send(get(USERS + "?size=2&page=0"), token, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(2)))
			.andExpect(jsonPath("$.size").value(2))
			.andExpect(jsonPath("$.totalItems").value((int) users.count()))
			.andExpect(content().string(not(containsString("passwordHash"))))
			.andExpect(content().string(not(containsString("password_hash"))));
		send(get(USERS + "?q=" + one.user().email()), token, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].id").value(one.user().id().intValue()));
		// LIKE wildcards typed by a person are literal text, not patterns
		send(get(USERS + "?q=%25"), token, null).andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));
		send(get(USERS + "?q=nobody-has-this-name"), token, null).andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));
	}

	@Test
	void theIssuedCredentialNeverPrintsItsPassword() {
		AdminUserDto user = new AdminUserDto(1L, "A", "a@b.co", "+15550000", Role.APPLICANT_DRIVER, true, true, true, null, null);
		String printed = new IssuedCredentialDto(user, "Super-Secret#Temp123", Instant.now()).toString();
		assertThat(printed).doesNotContain("Super-Secret#Temp123");
	}
}
