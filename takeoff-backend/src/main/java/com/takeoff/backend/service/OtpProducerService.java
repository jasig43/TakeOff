package com.takeoff.backend.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import com.takeoff.backend.config.TakeoffProperties;
import com.takeoff.backend.dto.OtpEvent;
import com.takeoff.backend.exception.OtpDispatchException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Publishes {@code OTP_GENERATE} events to {@code takeoff.exchange}. */
@Service
public class OtpProducerService {

	private static final Logger log = LoggerFactory.getLogger(OtpProducerService.class);

	private final RabbitTemplate rabbitTemplate;
	private final ObjectMapper objectMapper;
	private final TakeoffProperties.Rabbit rabbit;
	private final Clock clock;

	public OtpProducerService(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper, TakeoffProperties properties,
			Clock clock) {
		this.rabbitTemplate = rabbitTemplate;
		this.objectMapper = objectMapper;
		this.rabbit = properties.rabbitmq();
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
			Message message = MessageBuilder.withBody(objectMapper.writeValueAsBytes(event))
				.setContentType(MessageProperties.CONTENT_TYPE_JSON)
				.setContentEncoding(StandardCharsets.UTF_8.name())
				.setDeliveryMode(MessageDeliveryMode.PERSISTENT)
				.build();
			rabbitTemplate.send(rabbit.exchange(), rabbit.routingKey(), message);
			log.debug("Published {} for user {}", event.eventType(), userId);
		}
		catch (AmqpException | JacksonException ex) {
			log.error("Could not publish {} for user {}: {}", event.eventType(), userId, ex.getMessage());
			throw new OtpDispatchException(ex);
		}
	}
}
