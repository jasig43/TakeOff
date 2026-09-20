package com.takeoff.backend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for the OTP workflow: {@code takeoff.exchange} --(otp.routing.key)--> {@code otp.queue}.
 * Names are configurable ({@code takeoff.rabbitmq.*}). Spring's RabbitAdmin declares these on first connect.
 */
@Configuration
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
}
