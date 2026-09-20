package com.takeoff.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.takeoff.backend.config.TakeoffProperties;
import com.takeoff.backend.dto.DecisionEvent;
import com.takeoff.backend.model.ApplicationStatus;
import com.takeoff.backend.model.User;
import com.takeoff.backend.repository.UserRepository;
import com.takeoff.backend.sms.SmsDeliveryException;
import com.takeoff.backend.sms.SmsSender;

import tools.jackson.databind.ObjectMapper;

/**
 * Consumes {@code APPLICATION_DECIDED} events and texts the driver the outcome. The in-app notification was already
 * written when the decision was saved, so this listener only adds the SMS channel.
 *
 * <p>Like the OTP listener it never fails on bad input: malformed or unsupported events, unknown users and SMS
 * failures are logged (without phone numbers or message bodies) and dropped.
 */
@Component
public class NotificationConsumerListener {

	private static final Logger log = LoggerFactory.getLogger(NotificationConsumerListener.class);

	private final UserRepository users;
	private final SmsSender smsSender;
	private final ObjectMapper objectMapper;
	private final TakeoffProperties.TestBypass testBypass;

	public NotificationConsumerListener(UserRepository users, SmsSender smsSender, ObjectMapper objectMapper,
			TakeoffProperties properties) {
		this.users = users;
		this.smsSender = smsSender;
		this.objectMapper = objectMapper;
		this.testBypass = properties.otp().testBypass();
	}

	/** RabbitMQ entry point (ignored when the Redis transport is in use). */
	@RabbitListener(queues = "${takeoff.rabbitmq.notification-queue}")
	public void onMessage(Message message) {
		handle(message.getBody());
	}

	/** Handles one event, whichever transport delivered it. */
	public void handle(byte[] body) {
		DecisionEvent event;
		try {
			event = objectMapper.readValue(body, DecisionEvent.class);
		}
		catch (RuntimeException ex) {
			log.warn("Discarding malformed notification event ({} bytes): {}", body.length, ex.getClass().getSimpleName());
			return;
		}
		if (event == null || event.userId() == null || !DecisionEvent.APPLICATION_DECIDED.equals(event.eventType())
				|| event.referenceId() == null) {
			log.warn("Discarding unsupported notification event");
			return;
		}

		User user = users.findById(event.userId()).orElse(null);
		if (user == null) {
			log.warn("Discarding notification for unknown user {}", event.userId());
			return;
		}
		if (testBypass.enabled() && testBypass.phoneNumber().equals(user.getPhoneNumber())) {
			return; // fictional evaluator number: nobody to text
		}

		String text = ApplicationStatus.APPROVED.name().equals(event.status())
				? "TakeOFF: your driver application " + event.referenceId() + " has been approved."
				: "TakeOFF: your driver application " + event.referenceId()
						+ " was not approved. Sign in to TakeOFF to see the reason.";
		try {
			smsSender.send(user.getPhoneNumber(), text);
			log.info("Decision SMS sent for application {}", event.applicationId());
		}
		catch (SmsDeliveryException ex) {
			log.error("Decision SMS for application {} could not be sent: {}", event.applicationId(), ex.getMessage());
		}
		catch (RuntimeException ex) {
			log.error("Unexpected error sending the decision SMS for application {}", event.applicationId(), ex);
		}
	}
}
