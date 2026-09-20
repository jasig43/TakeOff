package com.takeoff.backend.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.takeoff.backend.config.TakeoffProperties;
import com.takeoff.backend.service.NotificationConsumerListener;
import com.takeoff.backend.service.OtpConsumerListener;

/**
 * Reads the Redis queues and hands each message to the same handler RabbitMQ would have called. One daemon thread per
 * topic, each blocking on its own list. A message that makes its handler throw is logged and dropped, and Redis
 * outages are retried after a pause, so neither can stop the loop.
 */
@Component
@ConditionalOnProperty(name = "takeoff.messaging.provider", havingValue = "redis")
public class RedisQueueWorker implements SmartLifecycle {

	private static final Logger log = LoggerFactory.getLogger(RedisQueueWorker.class);
	private static final Duration POLL = Duration.ofSeconds(2);
	private static final Duration RETRY_PAUSE = Duration.ofSeconds(2);

	private final ListQueue queue;
	private final TakeoffProperties.Rabbit names;
	private final Consumer<byte[]> otpHandler;
	private final Consumer<byte[]> notificationHandler;
	private final List<Thread> threads = new ArrayList<>();
	private volatile boolean running;

	@Autowired
	public RedisQueueWorker(ListQueue queue, TakeoffProperties properties, OtpConsumerListener otp,
			NotificationConsumerListener notification) {
		this(queue, properties, otp::handle, notification::handle);
	}

	/** Test seam: any handlers can be plugged in. Not used by Spring (the constructor above is marked @Autowired). */
	RedisQueueWorker(ListQueue queue, TakeoffProperties properties, Consumer<byte[]> otpHandler,
			Consumer<byte[]> notificationHandler) {
		this.queue = queue;
		this.names = properties.rabbitmq();
		this.otpHandler = otpHandler;
		this.notificationHandler = notificationHandler;
	}

	@Override
	public synchronized void start() {
		if (running) {
			return;
		}
		running = true;
		spawn(MessageTopic.OTP, otpHandler);
		spawn(MessageTopic.NOTIFICATION, notificationHandler);
		log.info("Redis queue worker started for {} and {}", RedisMessageBus.key(MessageTopic.OTP, names),
				RedisMessageBus.key(MessageTopic.NOTIFICATION, names));
	}

	private void spawn(MessageTopic topic, Consumer<byte[]> handler) {
		String key = RedisMessageBus.key(topic, names);
		Thread thread = new Thread(() -> loop(key, handler), "takeoff-queue-" + topic.name().toLowerCase());
		thread.setDaemon(true);
		threads.add(thread);
		thread.start();
	}

	private void loop(String key, Consumer<byte[]> handler) {
		while (running) {
			try {
				String message = queue.pop(key, POLL);
				if (message != null) {
					handler.accept(message.getBytes(StandardCharsets.UTF_8));
				}
			}
			catch (RuntimeException ex) {
				// Either the handler rejected this message or Redis is briefly unavailable. Neither ends the loop.
				log.warn("Queue {}: {}", key, ex.getMessage());
				pause();
			}
		}
	}

	private static void pause() {
		try {
			Thread.sleep(RETRY_PAUSE.toMillis());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public synchronized void stop() {
		running = false;
		threads.forEach(Thread::interrupt);
		threads.clear();
	}

	@Override
	public boolean isRunning() {
		return running;
	}
}
