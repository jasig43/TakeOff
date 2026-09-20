package com.takeoff.backend.service;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.takeoff.backend.dto.OtpEvent;
import com.takeoff.backend.exception.OtpDispatchException;
import com.takeoff.backend.messaging.MessageBus;
import com.takeoff.backend.messaging.MessageBusException;
import com.takeoff.backend.messaging.MessageTopic;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Publishes {@code OTP_GENERATE} events to the message bus (RabbitMQ locally, Redis on the hosted demo). */
@Service
public class OtpProducerService {

	private static final Logger log = LoggerFactory.getLogger(OtpProducerService.class);

	private final MessageBus bus;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	public OtpProducerService(MessageBus bus, ObjectMapper objectMapper, Clock clock) {
		this.bus = bus;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	/**
	 * Queues a request to generate an OTP for the user. The message holds only the event type, user id,
	 * phone number and timestamp: no password, token or code.
	 *
	 * @throws OtpDispatchException if the broker is unreachable or the event cannot be serialized
	 */
	public void publishOtpGenerate(Long userId, String phoneNumber) {
		OtpEvent event = OtpEvent.generate(userId, phoneNumber, clock.instant());
		try {
			bus.publish(MessageTopic.OTP, objectMapper.writeValueAsBytes(event));
			log.debug("Published {} for user {}", event.eventType(), userId);
		}
		catch (MessageBusException | JacksonException ex) {
			log.error("Could not publish {} for user {}: {}", event.eventType(), userId, ex.getMessage());
			throw new OtpDispatchException(ex);
		}
	}
}
