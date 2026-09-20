package com.takeoff.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.takeoff.backend.model.Role;
import com.takeoff.backend.repository.OtpTokenRepository;
import com.takeoff.backend.repository.UserRepository;
import com.takeoff.backend.service.OtpConsumerListener;

import tools.jackson.databind.ObjectMapper;

/**
 * The whole driver-application workflow and the separation between roles, over the real Spring context
 * (security chain, Flyway migrations on H2, real file storage under target/test-uploads). Only RabbitMQ is stubbed:
 * published messages are captured, and OTP events are handed to the real listener as the broker would.
 */
@TakeoffIntegrationTest
class ApplicationFlowIntegrationTest {

	private static final String EXCHANGE = "takeoff.exchange";
	private static final String OTP_KEY = "otp.routing.key";
	private static final String NOTIFICATION_KEY = "notification.routing.key";
	private static final String PASSWORD = "Sturdy#Password2026";
	private static final String ADMIN_EMAIL = "admin@takeoff.test";
	private static final String ADMIN_PASSWORD = "Test-Admin-Password#2026";
	private static final String APP = "/api/v1/drivers/application";
	private static final String ADMIN = "/api/v1/admin";

	private static final byte[] PDF = "%PDF-1.4\n%takeoff test document\n".getBytes(StandardCharsets.UTF_8);
	private static final AtomicInteger SEQUENCE = new AtomicInteger(1000);

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

	@MockitoBean
	RabbitTemplate rabbitTemplate;

	@BeforeEach
	void cleanUp() {
		otpTokens.deleteAll();
		users.findAll().stream().filter(user -> user.getRole() == Role.APPLICANT_DRIVER).forEach(users::delete);
	}

	// ------------------------------------------------------------------ helpers

	private record Driver(String email, String token) {
	}

	/** Builders are mutable and share an abstract base (plain and multipart requests), so the token is added in place. */
	private ResultActions send(AbstractMockHttpServletRequestBuilder<?> request, String token) throws Exception {
		if (token != null) {
			request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		return mvc.perform(request);
	}

	private ResultActions json(AbstractMockHttpServletRequestBuilder<?> request, String token, Object body)
			throws Exception {
		request.contentType(MediaType.APPLICATION_JSON);
		request.content(objectMapper.writeValueAsString(body));
		return send(request, token);
	}

	private String tokenFrom(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.accessToken");
	}

	/** Registers a driver, delivers the OTP through the real listener, verifies the phone, and returns the JWT. */
	private Driver newDriver() throws Exception {
		int n = SEQUENCE.incrementAndGet();
		String email = "driver" + n + "@example.com";
		String phone = "+2637712" + n;
		Map<String, Object> signUp = new LinkedHashMap<>();
		signUp.put("fullName", "Driver Number " + n);
		signUp.put("email", email);
		signUp.put("phoneNumber", phone);
		signUp.put("password", PASSWORD);
		signUp.put("termsAccepted", true);
		json(post("/api/v1/auth/register"), null, signUp).andExpect(status().isCreated());

		ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
		verify(rabbitTemplate, atLeastOnce()).send(eq(EXCHANGE), eq(OTP_KEY), message.capture());
		otpConsumer.onMessage(message.getValue());
		String code = otpTokens.findFirstByUserIdOrderByIdDesc(users.findByEmail(email).orElseThrow().getId())
			.orElseThrow()
			.getCode();
		ResultActions verified = json(post("/api/v1/auth/verify-otp"), null, Map.of("identifier", email, "otp", code))
			.andExpect(status().isOk());
		return new Driver(email, tokenFrom(verified));
	}

	private String adminToken() throws Exception {
		return tokenFrom(json(post("/api/v1/auth/login"), null, Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD))
			.andExpect(status().isOk()));
	}

	private static Map<String, Object> personal() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("dateOfBirth", "1990-05-14");
		body.put("addressLine", "12 Samora Machel Avenue");
		body.put("city", "Harare");
		body.put("emergencyContactName", "Tendai Moyo");
		body.put("emergencyContactPhone", "+263771112222");
		return body;
	}

	private static Map<String, Object> identity(String nationalId) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("nationalId", nationalId);
		body.put("licenceNumber", "AB123456");
		body.put("licenceClass", "4");
		body.put("licenceExpiry", LocalDate.now().plusYears(2).toString());
		return body;
	}

	private static Map<String, Object> vehicle(String plate) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("vehicleType", "VAN");
		body.put("plateNumber", plate);
		body.put("make", "Toyota");
		body.put("model", "Hiace");
		return body;
	}

	private ResultActions upload(String token, String type, String filename, byte[] bytes) throws Exception {
		return send(multipart(APP + "/documents/" + type).file(new MockMultipartFile("file", filename, "application/pdf", bytes)),
				token);
	}

	private void fillEverything(Driver driver, String nationalId, String plate) throws Exception {
		json(put(APP + "/personal"), driver.token(), personal()).andExpect(status().isOk());
		json(put(APP + "/identity"), driver.token(), identity(nationalId)).andExpect(status().isOk());
		json(put(APP + "/vehicle"), driver.token(), vehicle(plate)).andExpect(status().isOk());
		for (String type : new String[] { "DRIVERS_LICENCE", "VEHICLE_REGISTRATION", "INSURANCE" }) {
			upload(driver.token(), type, type.toLowerCase() + ".pdf", PDF).andExpect(status().isOk());
		}
	}

	/** A driver with a complete, submitted application; returns the application id. */
	private long submittedApplication(Driver driver, String nationalId, String plate) throws Exception {
		fillEverything(driver, nationalId, plate);
		String body = send(post(APP + "/submit"), driver.token()).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	// ------------------------------------------------------------------ driver: the application

	@Test
	void aNewDriverStartsWithAnEmptyEditableDraft() throws Exception {
		Driver driver = newDriver();

		send(get(APP), driver.token()).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.editable").value(true))
			.andExpect(jsonPath("$.referenceId").doesNotExist())
			.andExpect(jsonPath("$.documents", hasSize(0)))
			.andExpect(jsonPath("$.progress.personal").value(false))
			.andExpect(jsonPath("$.progress.readyToSubmit").value(false));
	}

	@Test
	void personalDetailsAreValidatedPerFieldAndSaved() throws Exception {
		Driver driver = newDriver();

		Map<String, Object> minor = personal();
		minor.put("dateOfBirth", LocalDate.now().minusYears(17).toString());
		json(put(APP + "/personal"), driver.token(), minor).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'dateOfBirth')].message", hasItem(containsString("18"))));

		Map<String, Object> future = personal();
		future.put("dateOfBirth", LocalDate.now().plusDays(1).toString());
		json(put(APP + "/personal"), driver.token(), future).andExpect(status().isBadRequest());

		Map<String, Object> bad = personal();
		bad.put("city", " ");
		bad.put("emergencyContactPhone", "0771112222");
		json(put(APP + "/personal"), driver.token(), bad).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("city")))
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("emergencyContactPhone")));

		json(put(APP + "/personal"), driver.token(), personal()).andExpect(status().isOk())
			.andExpect(jsonPath("$.personal.city").value("Harare"))
			.andExpect(jsonPath("$.personal.dateOfBirth").value("1990-05-14"))
			.andExpect(jsonPath("$.progress.personal").value(true))
			.andExpect(jsonPath("$.progress.readyToSubmit").value(false));
	}

	@Test
	void identityRejectsAnExpiredLicenceAndADuplicateNationalId() throws Exception {
		Driver first = newDriver();
		Driver second = newDriver();

		Map<String, Object> expired = identity("63-123456 A 42");
		expired.put("licenceExpiry", LocalDate.now().minusDays(1).toString());
		json(put(APP + "/identity"), first.token(), expired).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'licenceExpiry')]").exists());

		json(put(APP + "/identity"), first.token(), identity("63-123456 A 42")).andExpect(status().isOk())
			.andExpect(jsonPath("$.identity.nationalId").value("63-123456 A 42"))
			.andExpect(jsonPath("$.progress.identity").value(true));

		// same person, different spelling/case: still a duplicate
		json(put(APP + "/identity"), second.token(), identity("63-123456  a  42")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("NATIONAL_ID_IN_USE"));
		// the owner can re-save their own
		json(put(APP + "/identity"), first.token(), identity("63-123456 A 42")).andExpect(status().isOk());
	}

	@Test
	void vehicleIsValidatedAndPlatesAreUnique() throws Exception {
		Driver first = newDriver();
		Driver second = newDriver();

		Map<String, Object> badType = vehicle("ABC 1234");
		badType.put("vehicleType", "SPACESHIP");
		json(put(APP + "/vehicle"), first.token(), badType).andExpect(status().isBadRequest());

		Map<String, Object> noMake = vehicle("ABC 1234");
		noMake.put("make", "");
		json(put(APP + "/vehicle"), first.token(), noMake).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("make")));

		json(put(APP + "/vehicle"), first.token(), vehicle("abc 1234")).andExpect(status().isOk())
			.andExpect(jsonPath("$.vehicle.plateNumber").value("ABC 1234")) // normalised to upper case
			.andExpect(jsonPath("$.vehicle.vehicleType").value("VAN"));
		json(put(APP + "/vehicle"), second.token(), vehicle("ABC 1234")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PLATE_IN_USE"));
	}

	@Test
	void documentsMustReallyBePdfPngOrJpegAndAreSizeLimited() throws Exception {
		Driver driver = newDriver();

		upload(driver.token(), "DRIVERS_LICENCE", "licence.pdf", PDF).andExpect(status().isOk())
			.andExpect(jsonPath("$.documents", hasSize(1)))
			.andExpect(jsonPath("$.documents[0].type").value("DRIVERS_LICENCE"))
			.andExpect(jsonPath("$.documents[0].contentType").value("application/pdf"))
			.andExpect(jsonPath("$.documents[0].sizeBytes").value(PDF.length));

		// a script renamed to .pdf is refused, whatever the client claims
		upload(driver.token(), "INSURANCE", "policy.pdf", "#!/bin/sh\nrm -rf /".getBytes(StandardCharsets.UTF_8))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
		upload(driver.token(), "INSURANCE", "empty.pdf", new byte[0]).andExpect(status().isBadRequest());

		byte[] big = Arrays.copyOf(PDF, 5 * 1024 * 1024 + 1);
		upload(driver.token(), "INSURANCE", "big.pdf", big).andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

		// unknown document type in the URL
		send(multipart(APP + "/documents/PASSPORT").file(new MockMultipartFile("file", "x.pdf", "application/pdf", PDF)),
				driver.token())
			.andExpect(status().isBadRequest());
	}

	@Test
	void aMaliciousFilenameIsNeutralisedAndReuploadingReplacesTheDocument() throws Exception {
		Driver driver = newDriver();

		upload(driver.token(), "DRIVERS_LICENCE", "../../windows/system32/evil.pdf", PDF).andExpect(status().isOk())
			.andExpect(jsonPath("$.documents[0].filename").value("evil.pdf"));

		byte[] newer = "%PDF-1.7\nsecond version\n".getBytes(StandardCharsets.UTF_8);
		upload(driver.token(), "DRIVERS_LICENCE", "licence-v2.pdf", newer).andExpect(status().isOk())
			.andExpect(jsonPath("$.documents", hasSize(1)))
			.andExpect(jsonPath("$.documents[0].filename").value("licence-v2.pdf"));
		byte[] downloaded = send(get(APP + "/documents/DRIVERS_LICENCE"), driver.token()).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsByteArray();
		assertThat(downloaded).isEqualTo(newer);

		send(delete(APP + "/documents/DRIVERS_LICENCE"), driver.token()).andExpect(status().isOk())
			.andExpect(jsonPath("$.documents", hasSize(0)));
		send(get(APP + "/documents/DRIVERS_LICENCE"), driver.token()).andExpect(status().isNotFound());
	}

	@Test
	void submittingNeedsEverythingThenAssignsAReferenceAndLocksTheApplication() throws Exception {
		Driver driver = newDriver();

		json(put(APP + "/personal"), driver.token(), personal()).andExpect(status().isOk());
		send(post(APP + "/submit"), driver.token()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("APPLICATION_INCOMPLETE"))
			.andExpect(jsonPath("$.message", containsString("identity and licence")))
			.andExpect(jsonPath("$.message", containsString("vehicle details")))
			.andExpect(jsonPath("$.message", containsString("insurance document")));

		fillEverything(driver, "63-200300 B 11", "XYZ 9876");
		send(get(APP), driver.token()).andExpect(jsonPath("$.progress.readyToSubmit").value(true));

		send(post(APP + "/submit"), driver.token()).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
			.andExpect(jsonPath("$.editable").value(false))
			.andExpect(jsonPath("$.referenceId", matchesPattern("TKO-\\d{8}-[A-HJ-NP-Z2-9]{6}")))
			.andExpect(jsonPath("$.submittedAt").isNotEmpty());

		// locked: no edits, no uploads, no second submission
		json(put(APP + "/personal"), driver.token(), personal()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("APPLICATION_LOCKED"));
		upload(driver.token(), "INSURANCE", "other.pdf", PDF).andExpect(status().isConflict());
		send(delete(APP + "/documents/INSURANCE"), driver.token()).andExpect(status().isConflict());
		send(post(APP + "/submit"), driver.token()).andExpect(status().isConflict());

		// and the driver was told
		send(get("/api/v1/drivers/notifications"), driver.token()).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].type").value("APPLICATION_SUBMITTED"))
			.andExpect(jsonPath("$.unreadCount").value(1));
	}

	// ------------------------------------------------------------------ admin: the review workflow

	@Test
	void theQueueShowsSubmittedApplicationsFiltersByStatusAndSearchesButHidesDrafts() throws Exception {
		Driver submitted = newDriver();
		Driver draftOnly = newDriver();
		submittedApplication(submitted, "63-111111 C 11", "QUE 0001");
		json(put(APP + "/personal"), draftOnly.token(), personal()).andExpect(status().isOk());
		String admin = adminToken();

		send(get(ADMIN + "/applications"), admin).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(1)) // the draft is invisible
			.andExpect(jsonPath("$.items[0].status").value("PENDING_REVIEW"))
			.andExpect(jsonPath("$.items[0].driverEmail").value(submitted.email()))
			.andExpect(jsonPath("$.items[0].plateNumber").value("QUE 0001"))
			.andExpect(jsonPath("$.items[0].referenceId", matchesPattern("TKO-\\d{8}-[A-Z2-9]{6}")));

		send(get(ADMIN + "/applications?status=PENDING_REVIEW"), admin).andExpect(jsonPath("$.totalItems").value(1));
		send(get(ADMIN + "/applications?status=APPROVED"), admin).andExpect(jsonPath("$.totalItems").value(0));
		send(get(ADMIN + "/applications?status=DRAFT"), admin).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_STATUS_FILTER"));
		send(get(ADMIN + "/applications?status=BOGUS"), admin).andExpect(status().isBadRequest());

		send(get(ADMIN + "/applications?q=que 0001"), admin).andExpect(jsonPath("$.totalItems").value(1)); // plate, case-insensitive
		send(get(ADMIN + "/applications?q=" + submitted.email()), admin).andExpect(jsonPath("$.totalItems").value(1));
		send(get(ADMIN + "/applications?q=nobody-by-this-name"), admin).andExpect(jsonPath("$.totalItems").value(0));
		send(get(ADMIN + "/applications?q=%25"), admin).andExpect(jsonPath("$.totalItems").value(0)); // '%' is text, not a wildcard
		send(get(ADMIN + "/applications?size=1&page=0"), admin).andExpect(jsonPath("$.totalPages").value(1));
	}

	@Test
	void anAdminCanInspectAnApplicationAndItsDocumentsButNotADraft() throws Exception {
		Driver driver = newDriver();
		long id = submittedApplication(driver, "63-222222 D 22", "INS 0002");
		Driver draftOnly = newDriver();
		String draftBody = send(get(APP), draftOnly.token()).andReturn().getResponse().getContentAsString();
		long draftId = ((Number) JsonPath.read(draftBody, "$.id")).longValue();
		String admin = adminToken();

		send(get(ADMIN + "/applications/" + id), admin).andExpect(status().isOk())
			.andExpect(jsonPath("$.driver.email").value(driver.email()))
			.andExpect(jsonPath("$.driver.phoneVerified").value(true))
			.andExpect(jsonPath("$.driver.passwordHash").doesNotExist())
			.andExpect(jsonPath("$.application.identity.nationalId").value("63-222222 D 22"))
			.andExpect(jsonPath("$.application.vehicle.plateNumber").value("INS 0002"))
			.andExpect(jsonPath("$.application.documents", hasSize(3)));

		byte[] bytes = send(get(ADMIN + "/applications/" + id + "/documents/INSURANCE"), admin).andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "application/pdf"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("Cache-Control", containsString("no-store")))
			.andReturn().getResponse().getContentAsByteArray();
		assertThat(bytes).isEqualTo(PDF);

		send(get(ADMIN + "/applications/" + draftId), admin).andExpect(status().isNotFound());
		send(get(ADMIN + "/applications/" + draftId + "/documents/INSURANCE"), admin).andExpect(status().isNotFound());
		send(get(ADMIN + "/applications/999999"), admin).andExpect(status().isNotFound());
	}

	@Test
	void approvingPersistsTheDecisionNotifiesTheDriverAndPublishesAnEvent() throws Exception {
		Driver driver = newDriver();
		long id = submittedApplication(driver, "63-333333 E 33", "APP 0003");
		String admin = adminToken();

		json(patch(ADMIN + "/applications/" + id + "/status"), admin, Map.of("status", "APPROVED", "note", "Welcome aboard"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.application.status").value("APPROVED"))
			.andExpect(jsonPath("$.application.decidedAt").isNotEmpty())
			.andExpect(jsonPath("$.application.decisionNote").value("Welcome aboard"));

		// persisted: the driver sees it, and it survives a fresh read
		send(get(APP), driver.token()).andExpect(jsonPath("$.status").value("APPROVED"))
			.andExpect(jsonPath("$.editable").value(false));
		send(get(ADMIN + "/summary"), admin).andExpect(jsonPath("$.approved").value(1))
			.andExpect(jsonPath("$.pendingReview").value(0))
			.andExpect(jsonPath("$.totalSubmitted").value(1));

		// in-app notification, unread
		send(get("/api/v1/drivers/notifications"), driver.token())
			.andExpect(jsonPath("$.items[*].type", hasItem("APPLICATION_APPROVED")))
			.andExpect(jsonPath("$.unreadCount").value(2)); // submitted + approved

		// RabbitMQ event for the SMS channel: ids and outcome only
		ArgumentCaptor<Message> event = ArgumentCaptor.forClass(Message.class);
		verify(rabbitTemplate).send(eq(EXCHANGE), eq(NOTIFICATION_KEY), event.capture());
		String payload = new String(event.getValue().getBody(), StandardCharsets.UTF_8);
		assertThat((String) JsonPath.read(payload, "$.eventType")).isEqualTo("APPLICATION_DECIDED");
		assertThat((String) JsonPath.read(payload, "$.status")).isEqualTo("APPROVED");
		assertThat((String) JsonPath.read(payload, "$.referenceId")).startsWith("TKO-");
		assertThat(payload).doesNotContain("Welcome aboard").doesNotContain("63-333333").doesNotContain("APP 0003");

		// a decision is final: deciding again is refused
		json(patch(ADMIN + "/applications/" + id + "/status"), admin, Map.of("status", "REJECTED", "note", "changed my mind"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
	}

	@Test
	void rejectingNeedsAReasonAndTheDriverCanReviseAndResubmit() throws Exception {
		Driver driver = newDriver();
		long id = submittedApplication(driver, "63-444444 F 44", "REJ 0004");
		String admin = adminToken();
		String url = ADMIN + "/applications/" + id + "/status";

		json(patch(url), admin, Map.of("status", "REJECTED")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("note")));
		json(patch(url), admin, Map.of("status", "DRAFT")).andExpect(status().isBadRequest());
		json(patch(url), admin, Map.of("status", "PENDING_REVIEW")).andExpect(status().isBadRequest());
		send(get(APP), driver.token()).andExpect(jsonPath("$.status").value("PENDING_REVIEW")); // nothing changed

		json(patch(url), admin, Map.of("status", "REJECTED", "note", "Insurance document is unreadable."))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.application.status").value("REJECTED"));

		send(get(APP), driver.token()).andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.decisionNote").value("Insurance document is unreadable."))
			.andExpect(jsonPath("$.editable").value(true));
		send(get("/api/v1/drivers/notifications"), driver.token())
			.andExpect(jsonPath("$.items[?(@.type == 'APPLICATION_REJECTED')].message",
					hasItem(containsString("Insurance document is unreadable."))));

		// resubmitting without changing anything is pointless and refused
		send(post(APP + "/submit"), driver.token()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("APPLICATION_UNCHANGED"));

		// fixing something reopens it as a draft; the reference id is kept
		String reference = JsonPath.read(send(get(APP), driver.token()).andReturn().getResponse().getContentAsString(),
				"$.referenceId");
		upload(driver.token(), "INSURANCE", "insurance-clear-scan.pdf", PDF).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"));
		send(post(APP + "/submit"), driver.token()).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
			.andExpect(jsonPath("$.referenceId").value(reference))
			.andExpect(jsonPath("$.decisionNote").doesNotExist());

		json(patch(url), admin, Map.of("status", "APPROVED")).andExpect(status().isOk())
			.andExpect(jsonPath("$.application.status").value("APPROVED"));
	}

	@Test
	void ifRabbitMqIsDownTheDecisionAndTheInAppNotificationAreStillSaved() throws Exception {
		Driver driver = newDriver();
		long id = submittedApplication(driver, "63-555555 G 55", "MQ 0005");
		org.mockito.Mockito.doThrow(new org.springframework.amqp.AmqpConnectException(new RuntimeException("broker down")))
			.when(rabbitTemplate)
			.send(eq(EXCHANGE), eq(NOTIFICATION_KEY), any(Message.class));

		json(patch(ADMIN + "/applications/" + id + "/status"), adminToken(), Map.of("status", "APPROVED"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.application.status").value("APPROVED"));

		send(get("/api/v1/drivers/notifications"), driver.token())
			.andExpect(jsonPath("$.items[*].type", hasItem("APPLICATION_APPROVED")));
	}

	@Test
	void notificationsCanBeMarkedReadAndAreScopedToTheirOwner() throws Exception {
		Driver alice = newDriver();
		Driver bob = newDriver();
		submittedApplication(alice, "63-666666 H 66", "NOT 0006");

		String body = send(get("/api/v1/drivers/notifications"), alice.token()).andReturn().getResponse().getContentAsString();
		long notificationId = ((Number) JsonPath.read(body, "$.items[0].id")).longValue();

		// another driver cannot touch it
		send(post("/api/v1/drivers/notifications/" + notificationId + "/read"), bob.token()).andExpect(status().isNotFound());
		send(get("/api/v1/drivers/notifications"), alice.token()).andExpect(jsonPath("$.unreadCount").value(1));
		send(get("/api/v1/drivers/notifications"), bob.token()).andExpect(jsonPath("$.items", hasSize(0)));

		send(post("/api/v1/drivers/notifications/" + notificationId + "/read"), alice.token()).andExpect(status().isOk())
			.andExpect(jsonPath("$.unreadCount").value(0));
		send(post("/api/v1/drivers/notifications/read-all"), alice.token()).andExpect(status().isOk());
	}

	// ------------------------------------------------------------------ each role sees only its own area

	@Test
	void driversCannotUseAdminEndpointsAndAdminsCannotUseDriverEndpoints() throws Exception {
		Driver driver = newDriver();
		long id = submittedApplication(driver, "63-777777 J 77", "RBAC 0007");
		String admin = adminToken();

		for (String path : new String[] { ADMIN + "/summary", ADMIN + "/applications", ADMIN + "/applications/" + id,
				ADMIN + "/applications/" + id + "/documents/INSURANCE", ADMIN + "/health" }) {
			send(get(path), driver.token()).andExpect(status().isForbidden());
		}
		json(patch(ADMIN + "/applications/" + id + "/status"), driver.token(), Map.of("status", "APPROVED"))
			.andExpect(status().isForbidden());
		send(get(APP), driver.token()).andExpect(jsonPath("$.status").value("PENDING_REVIEW")); // untouched

		for (String path : new String[] { APP, APP + "/documents/INSURANCE", "/api/v1/drivers/notifications",
				"/api/v1/drivers/profile" }) {
			send(get(path), admin).andExpect(status().isForbidden());
		}
		json(put(APP + "/personal"), admin, personal()).andExpect(status().isForbidden());
		send(post(APP + "/submit"), admin).andExpect(status().isForbidden());
		send(multipart(APP + "/documents/INSURANCE").file(new MockMultipartFile("file", "x.pdf", "application/pdf", PDF)),
				admin)
			.andExpect(status().isForbidden());
	}

	@Test
	void everythingRequiresAuthenticationAndADriverOnlyEverSeesTheirOwnData() throws Exception {
		send(get(APP), null).andExpect(status().isUnauthorized());
		send(get(ADMIN + "/applications"), null).andExpect(status().isUnauthorized());
		send(get("/api/v1/drivers/notifications"), null).andExpect(status().isUnauthorized());
		send(post(APP + "/submit"), null).andExpect(status().isUnauthorized());

		Driver alice = newDriver();
		Driver bob = newDriver();
		submittedApplication(alice, "63-888888 K 88", "OWN 0008");

		// Bob's view is his own (empty) draft, and he cannot reach Alice's documents through his endpoints
		send(get(APP), bob.token()).andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.identity.nationalId").doesNotExist())
			.andExpect(jsonPath("$.documents", hasSize(0)));
		send(get(APP + "/documents/INSURANCE"), bob.token()).andExpect(status().isNotFound());
		send(get(APP), alice.token()).andExpect(jsonPath("$.identity.nationalId").value("63-888888 K 88"));

		verify(rabbitTemplate, never()).send(eq(EXCHANGE), eq(NOTIFICATION_KEY), any(Message.class)); // no decision yet
	}
}
