package com.takeoff.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.takeoff.backend.dto.DecisionEvent;
import com.takeoff.backend.messaging.MessageBus;
import com.takeoff.backend.messaging.MessageBusException;
import com.takeoff.backend.messaging.MessageTopic;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes {@code APPLICATION_DECIDED} events. Best effort by design: the decision and the driver's in-app
 * notification are already committed to the database before this runs, so a broker outage only delays the SMS channel.
 */
@Service
public class NotificationProducerService {

	private static final Logger log = LoggerFactory.getLogger(NotificationProducerService.class);

	private final MessageBus bus;
	private final ObjectMapper objectMapper;

	public NotificationProducerService(MessageBus bus, ObjectMapper objectMapper) {
		this.bus = bus;
		this.objectMapper = objectMapper;
	}

	/** @return true if the event was handed to the broker */
	public boolean publishDecision(DecisionEvent event) {
		try {
			bus.publish(MessageTopic.NOTIFICATION, objectMapper.writeValueAsBytes(event));
			return true;
		}
		catch (MessageBusException | JacksonException ex) {
			log.error("Could not publish {} for application {}: {}. The driver still has the in-app notification.",
					event.eventType(), event.applicationId(), ex.getMessage());
			return false;
		}
	}
}
