package com.takeoff.backend.messaging;

import java.nio.charset.StandardCharsets;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.takeoff.backend.config.TakeoffProperties;

/** The default transport: publishes to {@code takeoff.exchange} with the routing key for the topic. */
@Component
@ConditionalOnProperty(name = "takeoff.messaging.provider", havingValue = "rabbitmq", matchIfMissing = true)
public class RabbitMessageBus implements MessageBus {

	private final RabbitTemplate rabbitTemplate;
	private final TakeoffProperties.Rabbit rabbit;

	public RabbitMessageBus(RabbitTemplate rabbitTemplate, TakeoffProperties properties) {
		this.rabbitTemplate = rabbitTemplate;
		this.rabbit = properties.rabbitmq();
	}

	@Override
	public void publish(MessageTopic topic, byte[] jsonBody) {
		String routingKey = topic == MessageTopic.OTP ? rabbit.routingKey() : rabbit.notificationRoutingKey();
		try {
			Message message = MessageBuilder.withBody(jsonBody)
				.setContentType(MessageProperties.CONTENT_TYPE_JSON)
				.setContentEncoding(StandardCharsets.UTF_8.name())
				.setDeliveryMode(MessageDeliveryMode.PERSISTENT)
				.build();
			rabbitTemplate.send(rabbit.exchange(), routingKey, message);
		}
		catch (AmqpException ex) {
			throw new MessageBusException("RabbitMQ did not accept the message: " + ex.getMessage(), ex);
		}
	}
}
