package com.takeoff.backend.messaging;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.takeoff.backend.config.TakeoffProperties;

/**
 * The hosted-demo transport: each topic is a Redis list, named after the RabbitMQ queue it replaces
 * ({@code takeoff:otp.queue}, {@code takeoff:notification.queue}).
 */
@Component
@ConditionalOnProperty(name = "takeoff.messaging.provider", havingValue = "redis")
public class RedisMessageBus implements MessageBus {

	private final ListQueue queue;
	private final TakeoffProperties.Rabbit names;

	public RedisMessageBus(ListQueue queue, TakeoffProperties properties) {
		this.queue = queue;
		this.names = properties.rabbitmq();
	}

	/** The Redis key for a topic; shared with the worker that reads it. */
	public static String key(MessageTopic topic, TakeoffProperties.Rabbit names) {
		return "takeoff:" + (topic == MessageTopic.OTP ? names.queue() : names.notificationQueue());
	}

	@Override
	public void publish(MessageTopic topic, byte[] jsonBody) {
		queue.push(key(topic, names), new String(jsonBody, StandardCharsets.UTF_8));
	}
}
