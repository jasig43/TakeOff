package com.takeoff.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

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

import com.jayway.jsonpath.JsonPath;
import com.takeoff.backend.dto.ChangePasswordRequest;
import com.takeoff.backend.model.Role;
import com.takeoff.backend.model.User;
import com.takeoff.backend.repository.UserRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * An administrator changing their own password, over the real security chain. It uses its own throw-away accounts so the
 * seeded admin that other tests sign in with is never touched.
 */
@TakeoffIntegrationTest
class AdminAccountIntegrationTest {

	private static final String ADMIN_EMAIL = "settings-admin@takeoff.test";
	private static final String DRIVER_EMAIL = "settings-driver@takeoff.test";
	private static final String OLD_PASSWORD = "Settings-Admin#Password1";
	private static final String NEW_PASSWORD = "Brand-New#Password2027";
	private static final String URL = "/api/v1/admin/account/password";

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
		User admin = new User("Settings Admin", ADMIN_EMAIL, "+15550771", encoder.encode(OLD_PASSWORD), Role.LOGISTICS_ADMIN);
		admin.setPhoneVerified(true);
		users.save(admin);
		User driver = new User("Settings Driver", DRIVER_EMAIL, "+15550772", encoder.encode(OLD_PASSWORD), Role.APPLICANT_DRIVER);
		driver.setPhoneVerified(true);
		users.save(driver);
	}

	@AfterEach
	void removeAccounts() {
		users.findByEmail(ADMIN_EMAIL).ifPresent(users::delete);
		users.findByEmail(DRIVER_EMAIL).ifPresent(users::delete);
	}

	private ResultActions login(String email, String password) throws Exception {
		return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
	}

	private String token(String email) throws Exception {
		String body = login(email, OLD_PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

	private ResultActions change(String token, String current, String next) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("currentPassword", current);
		body.put("newPassword", next);
		var request = put(URL).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
		if (token != null) {
			request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		return mvc.perform(request);
	}

	@Test
	void anAdminCanChangeTheirPasswordAndOnlyTheNewOneWorksAfterwards() throws Exception {
		String token = token(ADMIN_EMAIL);

		change(token, OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent()).andExpect(content().string(""));

		login(ADMIN_EMAIL, OLD_PASSWORD).andExpect(status().isUnauthorized());
		login(ADMIN_EMAIL, NEW_PASSWORD).andExpect(status().isOk());
		// the hash was replaced, and what is stored is a hash, never the password
		User admin = users.findByEmail(ADMIN_EMAIL).orElseThrow();
		assertThat(admin.getPasswordHash()).isNotEqualTo(NEW_PASSWORD);
		assertThat(encoder.matches(NEW_PASSWORD, admin.getPasswordHash())).isTrue();
	}

	@Test
	void aWrongCurrentPasswordIsAFieldErrorNotA401_soTheSessionIsNotDropped_andNothingChanges() throws Exception {
		String token = token(ADMIN_EMAIL);

		change(token, "Not-The-Current#Password9", NEW_PASSWORD).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'currentPassword')].message", hasItem(containsString("not correct"))));

		login(ADMIN_EMAIL, OLD_PASSWORD).andExpect(status().isOk());
		login(ADMIN_EMAIL, NEW_PASSWORD).andExpect(status().isUnauthorized());
	}

	@Test
	void theNewPasswordMustMeetTheSamePolicyAsRegistration() throws Exception {
		String token = token(ADMIN_EMAIL);

		change(token, OLD_PASSWORD, "short").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("newPassword")));
		change(token, OLD_PASSWORD, "alllowercase-but-long-enough!").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'newPassword')].message", hasItem(containsString("uppercase"))));
		change(token, OLD_PASSWORD, "NoSpecialCharsAtAll1").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'newPassword')].message", hasItem(containsString("special character"))));

		login(ADMIN_EMAIL, OLD_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void theNewPasswordMustDifferFromTheCurrentOne() throws Exception {
		change(token(ADMIN_EMAIL), OLD_PASSWORD, OLD_PASSWORD).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'newPassword')].message", hasItem(containsString("different"))));
	}

	@Test
	void bothFieldsAreRequired() throws Exception {
		String token = token(ADMIN_EMAIL);

		change(token, null, null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("currentPassword")))
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("newPassword")));
		change(token, "   ", NEW_PASSWORD).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("currentPassword")));
	}

	@Test
	void driversAndAnonymousCallersCannotUseTheAdminEndpoint() throws Exception {
		change(token(DRIVER_EMAIL), OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isForbidden());
		change(null, OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isUnauthorized());

		// the driver's password was not changed by the refused call
		login(DRIVER_EMAIL, OLD_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void theRequestNeverPrintsThePasswords() {
		String printed = new ChangePasswordRequest(OLD_PASSWORD, NEW_PASSWORD).toString();
		assertThat(printed).doesNotContain(OLD_PASSWORD).doesNotContain(NEW_PASSWORD);
	}
}
