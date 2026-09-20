package com.takeoff.backend.service;

import java.nio.charset.StandardCharsets;

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
import com.takeoff.backend.dto.DecisionEvent;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes {@code APPLICATION_DECIDED} events. Best effort by design: the decision and the driver's in-app
 * notification are already committed to MySQL before this runs, so a broker outage only delays the SMS channel.
 */
@Service
public class NotificationProducerService {

	private static final Logger log = LoggerFactory.getLogger(NotificationProducerService.class);

	private final RabbitTemplate rabbitTemplate;
	private final ObjectMapper objectMapper;
	private final TakeoffProperties.Rabbit rabbit;

	public NotificationProducerService(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper,
			TakeoffProperties properties) {
		this.rabbitTemplate = rabbitTemplate;
		this.objectMapper = objectMapper;
		this.rabbit = properties.rabbitmq();
	}

	/** @return true if the event was handed to the broker */
	public boolean publishDecision(DecisionEvent event) {
		try {
			Message message = MessageBuilder.withBody(objectMapper.writeValueAsBytes(event))
				.setContentType(MessageProperties.CONTENT_TYPE_JSON)
				.setContentEncoding(StandardCharsets.UTF_8.name())
				.setDeliveryMode(MessageDeliveryMode.PERSISTENT)
				.build();
			rabbitTemplate.send(rabbit.exchange(), rabbit.notificationRoutingKey(), message);
			return true;
		}
		catch (AmqpException | JacksonException ex) {
			log.error("Could not publish {} for application {}: {}. The driver still has the in-app notification.",
					event.eventType(), event.applicationId(), ex.getMessage());
			return false;
		}
	}
}
