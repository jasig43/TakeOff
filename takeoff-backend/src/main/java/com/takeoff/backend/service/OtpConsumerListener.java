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
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import com.takeoff.backend.config.EnvironmentProfiles;
import com.takeoff.backend.config.TakeoffProperties;
import com.takeoff.backend.dto.OtpEvent;
import com.takeoff.backend.model.OtpToken;
import com.takeoff.backend.model.User;
import com.takeoff.backend.repository.OtpTokenRepository;
import com.takeoff.backend.repository.UserRepository;
import com.takeoff.backend.sms.SmsDeliveryException;
import com.takeoff.backend.sms.SmsSender;

import tools.jackson.databind.ObjectMapper;

/**
 * Consumes {@code OTP_GENERATE} events from {@code otp.queue}, issues the verification code and texts it to the
 * user through the configured {@link SmsSender}.
 *
 * <p>Order matters: the code is stored in one database transaction, and only after that commits is the SMS sent, so
 * a slow SMS provider never holds a transaction open and a code is never sent that was not stored.
 *
 * <p>If the SMS cannot be delivered the failure is logged (without the code or the number) and the message is
 * dropped; the stored code stays valid and the user can press "Resend code". In the dev/test profiles the code is
 * also printed to the console. The fixed evaluator code for the test phone is never texted (it is a fictional
 * number, and the code is public).
 *
 * <p>Malformed or unsupported messages are logged and dropped without failing the listener, so a bad message can
 * never wedge the queue.
 */
@Component
public class OtpConsumerListener {

	private static final Logger log = LoggerFactory.getLogger(OtpConsumerListener.class);
	private static final int OTP_DIGITS = 6;

	private final UserRepository users;
	private final OtpTokenRepository otpTokens;
	private final OtpCodec otpCodec;
	private final SmsSender smsSender;
	private final ObjectMapper objectMapper;
	private final TransactionOperations transactions;
	private final TakeoffProperties.Otp otp;
	private final boolean devOrTest;
	private final Clock clock;
	private final SecureRandom secureRandom = new SecureRandom();

	public OtpConsumerListener(UserRepository users, OtpTokenRepository otpTokens, OtpCodec otpCodec,
			SmsSender smsSender, ObjectMapper objectMapper, TransactionOperations transactions,
			TakeoffProperties properties, Environment environment, Clock clock) {
		this.users = users;
		this.otpTokens = otpTokens;
		this.otpCodec = otpCodec;
		this.smsSender = smsSender;
		this.objectMapper = objectMapper;
		this.transactions = transactions;
		this.otp = properties.otp();
		this.devOrTest = environment.acceptsProfiles(EnvironmentProfiles.RELAXED);
		this.clock = clock;

		// Fail fast: a fixed, publicly documented code must never be live outside development.
		if (otp.testBypass().enabled() && !devOrTest) {
			throw new IllegalStateException("takeoff.otp.test-bypass.enabled must not be true outside the dev/demo/test "
					+ "profiles: it would give a publicly known code to the bypass phone number.");
		}
	}

	/** The code that was just stored, kept only long enough to text it after the transaction commits. */
	private record IssuedOtp(Long userId, String phoneNumber, String code, boolean testPhone) {
	}

	/** RabbitMQ entry point (ignored when the Redis transport is in use). */
	@RabbitListener(queues = "${takeoff.rabbitmq.queue}")
	public void onMessage(Message message) {
		handle(message.getBody());
	}

	/** Handles one event, whichever transport delivered it. */
	public void handle(byte[] body) {
		OtpEvent event = parse(body);
		if (event == null) {
			return;
		}
		IssuedOtp issued;
		try {
			issued = transactions.execute(status -> issueOtp(event));
		}
		catch (RuntimeException ex) {
			// e.g. database outage: drop the message (no redelivery loop); the user can request a resend.
			log.error("Failed to issue an OTP for user {}", event.userId(), ex);
			throw new AmqpRejectAndDontRequeueException("OTP issuance failed", ex);
		}
		if (issued != null) {
			deliver(issued);
		}
	}

	private OtpEvent parse(byte[] body) {
		OtpEvent event;
		try {
			event = objectMapper.readValue(body, OtpEvent.class);
		}
		catch (RuntimeException ex) {
			log.warn("Discarding malformed OTP event ({} bytes): {}", body.length, ex.getClass().getSimpleName());
			return null;
		}
		if (event == null || event.userId() == null || !OtpEvent.OTP_GENERATE.equals(event.eventType())) {
			log.warn("Discarding unsupported OTP event");
			return null;
		}
		return event;
	}

	/** Runs inside the transaction: stores the new code. Returns what must be texted, or null if nothing. */
	private IssuedOtp issueOtp(OtpEvent event) {
		User user = users.findById(event.userId()).orElse(null);
		if (user == null) {
			log.warn("Discarding OTP event for unknown user {}", event.userId());
			return null;
		}

		// The stored phone number is authoritative; the event's copy is informational.
		String phoneNumber = user.getPhoneNumber();
		boolean testPhone = isTestPhone(phoneNumber);
		String code = testPhone ? otp.testBypass().code() : generateCode();

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
		return new IssuedOtp(user.getId(), phoneNumber, code, testPhone);
	}

	/** Runs after the transaction has committed. Never throws: an SMS failure must not affect the queue. */
	private void deliver(IssuedOtp issued) {
		if (issued.testPhone()) {
			return; // fixed public code for a fictional number: there is nobody to text
		}
		long minutes = Math.max(1, (otp.expirationSeconds() + 59) / 60);
		String body = "Your TakeOFF verification code is " + issued.code() + ". It expires in " + minutes
				+ (minutes == 1 ? " minute" : " minutes") + ". Never share this code with anyone.";
		try {
			smsSender.send(issued.phoneNumber(), body);
			log.info("Verification code texted for user {}", issued.userId());
		}
		catch (SmsDeliveryException ex) {
			log.error("Verification code for user {} could not be texted: {}. The user can request a resend.",
					issued.userId(), ex.getMessage());
		}
		catch (RuntimeException ex) {
			log.error("Unexpected error texting the verification code for user {}", issued.userId(), ex);
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
