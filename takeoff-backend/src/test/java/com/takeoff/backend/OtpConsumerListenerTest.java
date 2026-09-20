package com.takeoff.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

import com.takeoff.backend.config.TakeoffProperties;
import com.takeoff.backend.dto.OtpEvent;
import com.takeoff.backend.model.OtpToken;
import com.takeoff.backend.model.Role;
import com.takeoff.backend.model.User;
import com.takeoff.backend.repository.OtpTokenRepository;
import com.takeoff.backend.repository.UserRepository;
import com.takeoff.backend.service.OtpCodec;
import com.takeoff.backend.service.OtpConsumerListener;
import com.takeoff.backend.sms.SmsDeliveryException;
import com.takeoff.backend.sms.SmsSender;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class OtpConsumerListenerTest {

	private static final String TEST_PHONE = "+15550199";

	@Mock
	UserRepository users;
	@Mock
	OtpTokenRepository otpTokens;
	@Mock
	SmsSender sms;

	private final ObjectMapper objectMapper = JsonMapper.builder().build();

	private OtpConsumerListener listener(boolean bypassEnabled, String... profiles) {
		TakeoffProperties properties = TestFixtures.properties(false, bypassEnabled);
		return new OtpConsumerListener(users, otpTokens, new OtpCodec(properties, TestFixtures.environment("test")), sms,
				objectMapper, TransactionOperations.withoutTransaction(), properties, TestFixtures.environment(profiles),
				TestFixtures.CLOCK);
	}

	private User user(String phone) {
		User user = new User("Test Driver", "driver@example.com", phone, "hash", Role.APPLICANT_DRIVER);
		ReflectionTestUtils.setField(user, "id", 7L);
		return user;
	}

	private Message eventFor(long userId, String phone) throws Exception {
		return json(objectMapper.writeValueAsString(OtpEvent.generate(userId, phone, TestFixtures.CLOCK.instant())));
	}

	private static Message json(String body) {
		return MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8)).build();
	}

	private OtpToken savedToken() {
		ArgumentCaptor<OtpToken> captor = ArgumentCaptor.forClass(OtpToken.class);
		verify(otpTokens).save(captor.capture());
		return captor.getValue();
	}

	@Test
	void theTestPhoneNumberAlwaysReceivesTheFixedCode123456() throws Exception {
		when(users.findById(7L)).thenReturn(Optional.of(user(TEST_PHONE)));

		listener(true, "test").onMessage(eventFor(7L, TEST_PHONE));

		OtpToken token = savedToken();
		assertThat(token.getCode()).isEqualTo("123456");
		assertThat(token.getUserId()).isEqualTo(7L);
		assertThat(token.isConsumed()).isFalse();
		assertThat(token.getExpiresAt()).isEqualTo(TestFixtures.CLOCK.instant().plusSeconds(300));
	}

	@Test
	void previouslyActiveTokensAreInvalidatedBeforeTheNewOneIsStored() throws Exception {
		when(users.findById(7L)).thenReturn(Optional.of(user(TEST_PHONE)));

		listener(true, "test").onMessage(eventFor(7L, TEST_PHONE));

		InOrder order = inOrder(otpTokens);
		order.verify(otpTokens).invalidateActiveTokens(7L);
		order.verify(otpTokens).save(any(OtpToken.class));
	}

	@Test
	void otherPhoneNumbersGetFreshCryptographicallyRandomSixDigitCodes() throws Exception {
		when(users.findById(7L)).thenReturn(Optional.of(user("+15550123")));
		OtpConsumerListener listener = listener(true, "test");

		int runs = 40;
		for (int i = 0; i < runs; i++) {
			listener.onMessage(eventFor(7L, "+15550123"));
		}

		ArgumentCaptor<OtpToken> captor = ArgumentCaptor.forClass(OtpToken.class);
		verify(otpTokens, times(runs)).save(captor.capture());
		List<String> codes = captor.getAllValues().stream().map(OtpToken::getCode).toList();
		assertThat(codes).allMatch(code -> code.matches("\\d{6}"));
		Set<String> distinct = new HashSet<>(codes);
		assertThat(distinct).hasSizeGreaterThan(1).doesNotContain("123456"); // 1-in-10^6 per run, effectively never
	}

	@Test
	void theFixedCodeIsNotUsedWhenTheBypassIsDisabled() throws Exception {
		when(users.findById(7L)).thenReturn(Optional.of(user(TEST_PHONE)));
		OtpConsumerListener listener = listener(false, "test");

		for (int i = 0; i < 10; i++) {
			listener.onMessage(eventFor(7L, TEST_PHONE));
		}

		ArgumentCaptor<OtpToken> captor = ArgumentCaptor.forClass(OtpToken.class);
		verify(otpTokens, times(10)).save(captor.capture());
		assertThat(captor.getAllValues()).extracting(OtpToken::getCode).doesNotContain("123456");
	}

	@Test
	void theBypassCannotBeEnabledOutsideDevOrTestProfiles() {
		assertThatThrownBy(() -> listener(true, "prod")).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("test-bypass");
		assertThatThrownBy(() -> listener(true)).isInstanceOf(IllegalStateException.class);
		assertThatCode(() -> listener(false, "prod")).doesNotThrowAnyException();
		assertThatCode(() -> listener(true, "dev")).doesNotThrowAnyException();
	}

	@Test
	void malformedMessagesAreDiscardedWithoutFailingTheListener() {
		OtpConsumerListener listener = listener(true, "test");

		assertThatCode(() -> listener.onMessage(json("{ this is not json"))).doesNotThrowAnyException();
		assertThatCode(() -> listener.onMessage(json(""))).doesNotThrowAnyException();
		assertThatCode(() -> listener.onMessage(json("[1,2,3]"))).doesNotThrowAnyException();
		assertThatCode(() -> listener.onMessage(json("null"))).doesNotThrowAnyException();

		verify(otpTokens, never()).save(any());
	}

	@Test
	void unsupportedEventTypesAndIncompleteEventsAreIgnored() {
		OtpConsumerListener listener = listener(true, "test");

		listener.onMessage(json("{\"eventType\":\"SOMETHING_ELSE\",\"userId\":7}"));
		listener.onMessage(json("{\"eventType\":\"OTP_GENERATE\"}")); // no userId

		verify(otpTokens, never()).save(any());
	}

	@Test
	void theCodeIsTextedToTheUsersPhoneAfterItHasBeenStored() throws Exception {
		when(users.findById(7L)).thenReturn(Optional.of(user("+263771234567")));

		listener(true, "test").onMessage(eventFor(7L, "+263771234567"));

		OtpToken stored = savedToken();
		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		InOrder order = inOrder(otpTokens, sms);
		order.verify(otpTokens).save(any(OtpToken.class));
		order.verify(sms).send(eq("+263771234567"), body.capture());
		assertThat(body.getValue()).contains(stored.getCode()).contains("expires in 5 minutes").contains("TakeOFF");
	}

	@Test
	void everyNewCodeIsTextedAndIsTheSameCodeThatWasStored() throws Exception {
		when(users.findById(7L)).thenReturn(Optional.of(user("+15550123")));
		OtpConsumerListener listener = listener(true, "test");

		listener.onMessage(eventFor(7L, "+15550123"));
		listener.onMessage(eventFor(7L, "+15550123"));

		ArgumentCaptor<OtpToken> stored = ArgumentCaptor.forClass(OtpToken.class);
		verify(otpTokens, times(2)).save(stored.capture());
		ArgumentCaptor<String> texts = ArgumentCaptor.forClass(String.class);
		verify(sms, times(2)).send(eq("+15550123"), texts.capture());
		for (int i = 0; i < 2; i++) {
			assertThat(texts.getAllValues().get(i)).contains(stored.getAllValues().get(i).getCode());
		}
	}

	@Test
	void theFixedTestPhoneCodeIsNeverTexted() throws Exception {
		when(users.findById(7L)).thenReturn(Optional.of(user(TEST_PHONE)));

		listener(true, "test").onMessage(eventFor(7L, TEST_PHONE));

		verify(otpTokens).save(any(OtpToken.class)); // still stored, so the fixed code works
		verify(sms, never()).send(anyString(), anyString());
	}

	@Test
	void aFailedSmsDoesNotFailTheListenerAndTheStoredCodeIsKept() throws Exception {
		when(users.findById(7L)).thenReturn(Optional.of(user("+263771234567")));
		doThrow(new SmsDeliveryException("Twilio rejected the message (HTTP 400, Twilio error 21211)")).when(sms)
			.send(anyString(), anyString());
		OtpConsumerListener listener = listener(true, "test");

		assertThatCode(() -> listener.onMessage(eventFor(7L, "+263771234567"))).doesNotThrowAnyException();

		verify(otpTokens).save(any(OtpToken.class)); // the user can still press "Resend code"
	}

	@Test
	void anUnexpectedSmsErrorIsAlsoContained() throws Exception {
		when(users.findById(7L)).thenReturn(Optional.of(user("+263771234567")));
		doThrow(new IllegalStateException("boom")).when(sms).send(anyString(), anyString());
		OtpConsumerListener listener = listener(true, "test");

		assertThatCode(() -> listener.onMessage(eventFor(7L, "+263771234567"))).doesNotThrowAnyException();
	}

	@Test
	void nothingIsTextedForMalformedOrUnknownUserEvents() throws Exception {
		when(users.findById(99L)).thenReturn(Optional.empty());
		OtpConsumerListener listener = listener(true, "test");

		listener.onMessage(json("{ not json"));
		listener.onMessage(eventFor(99L, "+263771234567"));

		verify(sms, never()).send(anyString(), anyString());
	}

	@Test
	void eventsForUnknownUsersAreIgnored() throws Exception {
		when(users.findById(99L)).thenReturn(Optional.empty());

		listener(true, "test").onMessage(eventFor(99L, TEST_PHONE));

		verify(otpTokens, never()).save(any());
	}
}
