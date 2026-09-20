package com.takeoff.backend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology, all on {@code takeoff.exchange}:
 * <ul>
 *   <li>{@code otp.routing.key} -> {@code otp.queue}: OTP generation requests</li>
 *   <li>{@code notification.routing.key} -> {@code notification.queue}: "application decided" events</li>
 * </ul>
 * Names are configurable ({@code takeoff.rabbitmq.*}). Spring's RabbitAdmin declares these on first connect.
 */
@Configuration
@ConditionalOnProperty(name = "takeoff.messaging.provider", havingValue = "rabbitmq", matchIfMissing = true)
public class RabbitMqConfig {

	@Bean
	DirectExchange takeoffExchange(TakeoffProperties properties) {
		return new DirectExchange(properties.rabbitmq().exchange(), true, false);
	}

	@Bean
	Queue otpQueue(TakeoffProperties properties) {
		return QueueBuilder.durable(properties.rabbitmq().queue()).build();
	}

	@Bean
	Binding otpBinding(Queue otpQueue, DirectExchange takeoffExchange, TakeoffProperties properties) {
		return BindingBuilder.bind(otpQueue).to(takeoffExchange).with(properties.rabbitmq().routingKey());
	}

	@Bean
	Queue notificationQueue(TakeoffProperties properties) {
		return QueueBuilder.durable(properties.rabbitmq().notificationQueue()).build();
	}

	@Bean
	Binding notificationBinding(Queue notificationQueue, DirectExchange takeoffExchange, TakeoffProperties properties) {
		return BindingBuilder.bind(notificationQueue)
			.to(takeoffExchange)
			.with(properties.rabbitmq().notificationRoutingKey());
	}
}
