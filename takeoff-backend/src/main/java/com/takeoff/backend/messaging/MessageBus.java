package com.takeoff.backend.messaging;

/**
 * Where events go between the code that raises them and the code that reacts to them (OTP generation, application
 * decisions). Locally that is RabbitMQ; on the free hosted demo, where no RabbitMQ is available, it is a Redis list.
 * Producers only see this interface, so they behave the same either way.
 */
public interface MessageBus {

	/** @throws MessageBusException if the broker cannot be reached, so callers can degrade gracefully */
	void publish(MessageTopic topic, byte[] jsonBody);
}
