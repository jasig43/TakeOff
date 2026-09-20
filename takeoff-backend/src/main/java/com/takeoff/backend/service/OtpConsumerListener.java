package com.takeoff.backend.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import com.takeoff.backend.config.TakeoffProperties;
import com.takeoff.backend.dto.OtpEvent;
import com.takeoff.backend.model.OtpToken;
import com.takeoff.backend.model.User;
import com.takeoff.backend.repository.OtpTokenRepository;
import com.takeoff.backend.repository.UserRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Consumes {@code OTP_GENERATE} events from {@code otp.queue} and issues the verification code.
 *
 * <p>Phase 1 has no SMS gateway. In the dev/test profiles the code is logged to the console; in every
 * other profile it is not delivered anywhere (an SMS provider is a planned next-phase integration), so
 * the code is never written to logs outside development.
 *
 * <p>Malformed or unsupported messages are logged and dropped without failing the listener, so a bad
 * message can never wedge the queue.
 */
@Component
public class OtpConsumerListener {

	private static final Logger log = LoggerFactory.getLogger(OtpConsumerListener.class);
	private static final int OTP_DIGITS = 6;

	private final UserRepository users;
	private final OtpTokenRepository otpTokens;
	private final OtpCodec otpCodec;
	private final ObjectMapper objectMapper;
	private final TransactionOperations transactions;
	private final TakeoffProperties.Otp otp;
	private final boolean devOrTest;
	private final Clock clock;
	private final SecureRandom secureRandom = new SecureRandom();

	public OtpConsumerListener(UserRepository users, OtpTokenRepository otpTokens, OtpCodec otpCodec,
			ObjectMapper objectMapper, TransactionOperations transactions, TakeoffProperties properties,
			Environment environment, Clock clock) {
		this.users = users;
		this.otpTokens = otpTokens;
		this.otpCodec = otpCodec;
		this.objectMapper = objectMapper;
		this.transactions = transactions;
		this.otp = properties.otp();
		this.devOrTest = environment.acceptsProfiles(Profiles.of("dev", "test"));
		this.clock = clock;

		// Fail fast: a fixed, publicly documented code must never be live outside development.
		if (otp.testBypass().enabled() && !devOrTest) {
			throw new IllegalStateException("takeoff.otp.test-bypass.enabled must not be true outside the dev/test "
					+ "profiles: it would give a publicly known code to the bypass phone number.");
		}
	}

	@RabbitListener(queues = "${takeoff.rabbitmq.queue}")
	public void onMessage(Message message) {
		OtpEvent event = parse(message);
		if (event == null) {
			return;
		}
		try {
			transactions.executeWithoutResult(status -> issueOtp(event));
		}
		catch (RuntimeException ex) {
			// e.g. database outage: drop the message (no redelivery loop); the user can request a resend.
			log.error("Failed to issue an OTP for user {}", event.userId(), ex);
			throw new AmqpRejectAndDontRequeueException("OTP issuance failed", ex);
		}
	}

	private OtpEvent parse(Message message) {
		OtpEvent event;
		try {
			event = objectMapper.readValue(message.getBody(), OtpEvent.class);
		}
		catch (RuntimeException ex) {
			log.warn("Discarding malformed OTP event ({} bytes): {}", message.getBody().length,
					ex.getClass().getSimpleName());
			return null;
		}
		if (event == null || event.userId() == null || !OtpEvent.OTP_GENERATE.equals(event.eventType())) {
			log.warn("Discarding unsupported OTP event");
			return null;
		}
		return event;
	}

	private void issueOtp(OtpEvent event) {
		User user = users.findById(event.userId()).orElse(null);
		if (user == null) {
			log.warn("Discarding OTP event for unknown user {}", event.userId());
			return;
		}

		// The stored phone number is authoritative; the event's copy is informational.
		String phoneNumber = user.getPhoneNumber();
		String code = isTestPhone(phoneNumber) ? otp.testBypass().code() : generateCode();

		// Only the newest code may work: burn any previously active ones first.
		otpTokens.invalidateActiveTokens(user.getId());

		Instant now = clock.instant();
		otpTokens.save(new OtpToken(user.getId(), otpCodec.encode(user.getId(), code),
				now.plusSeconds(otp.expirationSeconds()), now));

		if (otp.logToConsole() && devOrTest) {
			log.info("[DEV ONLY] Verification code for user {} ({}): {}", user.getId(), phoneNumber, code);
		}
		else {
			log.info("Issued a verification code for user {}", user.getId());
		}
	}

	private boolean isTestPhone(String phoneNumber) {
		return otp.testBypass().enabled() && otp.testBypass().phoneNumber().equals(phoneNumber);
	}

	/** Uniformly random 6-digit code from a CSPRNG, zero-padded. */
	String generateCode() {
		int bound = (int) Math.pow(10, OTP_DIGITS);
		return String.format("%0" + OTP_DIGITS + "d", secureRandom.nextInt(bound));
	}
}
