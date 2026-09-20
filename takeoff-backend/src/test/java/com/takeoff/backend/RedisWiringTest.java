package com.takeoff.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import com.takeoff.backend.config.TakeoffProperties;
import com.takeoff.backend.messaging.ListQueue;
import com.takeoff.backend.messaging.MessageBus;
import com.takeoff.backend.messaging.RedisMessageBus;
import com.takeoff.backend.messaging.RedisQueueWorker;
import com.takeoff.backend.service.NotificationConsumerListener;
import com.takeoff.backend.service.OtpConsumerListener;

/**
 * The hosted demo's Redis pieces have to be constructible by Spring, not just by hand in a unit test. (A live deploy
 * once failed at startup because the worker had two constructors and Spring could not choose.) This builds them
 * through a real application context, with a stand-in queue instead of a Redis server.
 */
class RedisWiringTest {

	private static class NoopQueue implements ListQueue {
		@Override
		public void push(String key, String value) {
		}

		@Override
		public String pop(String key, Duration timeout) {
			try {
				Thread.sleep(Math.min(timeout.toMillis(), 50));
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return null;
		}
	}

	@Test
	void springCanBuildTheRedisBusAndWorker_andTheWorkerStartsWithTheContext() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			// the Redis beans are conditional on this switch, exactly as in the demo profile
			context.getEnvironment().getPropertySources()
				.addFirst(new MapPropertySource("demo", Map.of("takeoff.messaging.provider", "redis")));
			context.registerBean(ListQueue.class, NoopQueue::new);
			context.registerBean(TakeoffProperties.class, () -> TestFixtures.properties(false, false));
			context.registerBean(OtpConsumerListener.class, () -> mock(OtpConsumerListener.class));
			context.registerBean(NotificationConsumerListener.class, () -> mock(NotificationConsumerListener.class));
			context.registerBean(RedisMessageBus.class);
			context.registerBean(RedisQueueWorker.class);

			context.refresh(); // fails with "No default constructor found" if a constructor is ambiguous

			assertThat(context.getBean(MessageBus.class)).isInstanceOf(RedisMessageBus.class);
			assertThat(context.getBean(RedisQueueWorker.class).isRunning()).isTrue();
		}
	}
}
