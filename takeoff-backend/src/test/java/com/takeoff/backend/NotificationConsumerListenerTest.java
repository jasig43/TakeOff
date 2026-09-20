package com.takeoff.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import com.takeoff.backend.dto.DecisionEvent;
import com.takeoff.backend.model.Role;
import com.takeoff.backend.model.User;
import com.takeoff.backend.repository.UserRepository;
import com.takeoff.backend.service.NotificationConsumerListener;
import com.takeoff.backend.sms.SmsDeliveryException;
import com.takeoff.backend.sms.SmsSender;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerListenerTest {

	@Mock
	UserRepository users;
	@Mock
	SmsSender sms;

	private final ObjectMapper objectMapper = JsonMapper.builder().build();
	private final NotificationConsumerListener listener() {
		return new NotificationConsumerListener(users, sms, objectMapper, TestFixtures.properties(false, true));
	}

	private User driver(String phone) {
		User user = new User("Test Driver", "driver@example.com", phone, "hash", Role.APPLICANT_DRIVER);
		ReflectionTestUtils.setField(user, "id", 7L);
		return user;
	}

	private Message event(String status) throws Exception {
		DecisionEvent event = DecisionEvent.of(11L, 7L, status, "TKO-20260920-ABC234", TestFixtures.CLOCK.instant());
		return json(objectMapper.writeValueAsString(event));
	}

	private static Message json(String body) {
		return MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8)).build();
	}

	@Test
	void anApprovalIsTextedToTheDriverWithTheReferenceId() throws Exception {
		when(users.findById(7L)).thenReturn(Optional.of(driver("+263771234567")));

		listener().onMessage(event("APPROVED"));

		ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
		verify(sms).send(eq("+263771234567"), text.capture());
		assertThat(text.getValue()).contains("TKO-20260920-ABC234").contains("approved").doesNotContain("not approved");
	}

	@Test
	void aRejectionIsTextedWithoutRevealingTheReviewersNote() throws Exception {
		when(users.findById(7L)).thenReturn(Optional.of(driver("+263771234567")));

		listener().onMessage(event("REJECTED"));

		ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
		verify(sms).send(eq("+263771234567"), text.capture());
		assertThat(text.getValue()).contains("TKO-20260920-ABC234").contains("not approved").contains("Sign in");
	}

	@Test
	void theFictionalEvaluatorNumberIsNeverTexted() throws Exception {
		when(users.findById(7L)).thenReturn(Optional.of(driver("+15550199")));

		listener().onMessage(event("APPROVED"));

		verify(sms, never()).send(anyString(), anyString());
	}

	@Test
	void anSmsFailureIsContained() throws Exception {
		when(users.findById(7L)).thenReturn(Optional.of(driver("+263771234567")));
		doThrow(new SmsDeliveryException("Twilio rejected the message (HTTP 400)")).when(sms).send(anyString(), anyString());
		NotificationConsumerListener listener = listener();

		assertThatCode(() -> listener.onMessage(event("APPROVED"))).doesNotThrowAnyException();
	}

	@Test
	void anUnexpectedErrorIsAlsoContained() throws Exception {
		when(users.findById(7L)).thenReturn(Optional.of(driver("+263771234567")));
		doThrow(new IllegalStateException("boom")).when(sms).send(anyString(), anyString());
		NotificationConsumerListener listener = listener();

		assertThatCode(() -> listener.onMessage(event("APPROVED"))).doesNotThrowAnyException();
	}

	@Test
	void malformedUnsupportedAndUnknownUserEventsAreDropped() throws Exception {
		when(users.findById(99L)).thenReturn(Optional.empty());
		NotificationConsumerListener listener = listener();

		listener.onMessage(json("{ not json"));
		listener.onMessage(json(""));
		listener.onMessage(json("[1,2]"));
		listener.onMessage(json("{\"eventType\":\"SOMETHING_ELSE\",\"userId\":7,\"referenceId\":\"X\"}"));
		listener.onMessage(json("{\"eventType\":\"APPLICATION_DECIDED\",\"referenceId\":\"X\"}")); // no user id
		listener.onMessage(json("{\"eventType\":\"APPLICATION_DECIDED\",\"userId\":7}")); // no reference
		listener.onMessage(json("{\"eventType\":\"APPLICATION_DECIDED\",\"userId\":99,\"referenceId\":\"X\",\"status\":\"APPROVED\"}"));

		verify(sms, never()).send(anyString(), anyString());
	}
}
