package com.takeoff.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.takeoff.backend.config.TakeoffProperties;
import com.takeoff.backend.dto.DecisionEvent;
import com.takeoff.backend.dto.OtpEvent;
import com.takeoff.backend.exception.OtpDispatchException;
import com.takeoff.backend.messaging.ListQueue;
import com.takeoff.backend.messaging.MessageBusException;
import com.takeoff.backend.messaging.MessageTopic;
import com.takeoff.backend.messaging.RedisMessageBus;
import com.takeoff.backend.messaging.RedisQueueWorker;
import com.takeoff.backend.service.NotificationProducerService;
import com.takeoff.backend.service.OtpProducerService;

import tools.jackson.databind.ObjectMapper;

/** The Redis transport used by the hosted demo, exercised against an in-memory stand-in for a Redis list. */
class RedisMessagingTest {

	private static final ObjectMapper JSON = new ObjectMapper();
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-20T10:00:00Z"), ZoneOffset.UTC);
	private static final TakeoffProperties PROPERTIES = TestFixtures.properties(false, false);
	private static final String OTP_KEY = "takeoff:otp.queue";
	private static final String NOTIFICATION_KEY = "takeoff:notification.queue";

	/** LPUSH / BRPOP semantics: pushed at the head, popped from the tail, so the oldest message comes out first. */
	static class InMemoryQueue implements ListQueue {
		final java.util.concurrent.ConcurrentMap<String, LinkedBlockingDeque<String>> lists = new java.util.concurrent.ConcurrentHashMap<>();
		volatile RuntimeException failPushWith;
		final AtomicInteger poppedWhileDown = new AtomicInteger();
		volatile RuntimeException failPopWith;

		private LinkedBlockingDeque<String> list(String key) {
			return lists.computeIfAbsent(key, k -> new LinkedBlockingDeque<>());
		}

		@Override
		public void push(String key, String value) {
			if (failPushWith != null) {
				throw failPushWith;
			}
			list(key).addFirst(value);
		}

		@Override
		public String pop(String key, Duration timeout) {
			if (failPopWith != null) {
				poppedWhileDown.incrementAndGet();
				throw failPopWith;
			}
			try {
				return list(key).pollLast(Math.min(timeout.toMillis(), 100), TimeUnit.MILLISECONDS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return null;
			}
		}
	}

	private final InMemoryQueue queue = new InMemoryQueue();
	private RedisQueueWorker worker;

	@AfterEach
	void stopWorker() {
		if (worker != null) {
			worker.stop();
		}
	}

	private RedisQueueWorker worker(Consumer<byte[]> otp, Consumer<byte[]> notification) throws Exception {
		var constructor = RedisQueueWorker.class.getDeclaredConstructor(ListQueue.class, TakeoffProperties.class,
				Consumer.class, Consumer.class);
		constructor.setAccessible(true);
		worker = constructor.newInstance(queue, PROPERTIES, otp, notification);
		return worker;
	}

	// ------------------------------------------------------------------ publishing

	@Test
	void eachTopicHasItsOwnListNamedAfterTheRabbitQueueItReplaces() {
		RedisMessageBus bus = new RedisMessageBus(queue, PROPERTIES);

		bus.publish(MessageTopic.OTP, "{\"a\":1}".getBytes(StandardCharsets.UTF_8));
		bus.publish(MessageTopic.NOTIFICATION, "{\"b\":2}".getBytes(StandardCharsets.UTF_8));

		assertThat(queue.lists.get(OTP_KEY)).containsExactly("{\"a\":1}");
		assertThat(queue.lists.get(NOTIFICATION_KEY)).containsExactly("{\"b\":2}");
	}

	@Test
	void theOtpProducerQueuesAnEventWithNoSecretInIt() throws Exception {
		OtpProducerService producer = new OtpProducerService(new RedisMessageBus(queue, PROPERTIES), JSON, CLOCK);

		producer.publishOtpGenerate(42L, "+263771234567");

		String json = queue.lists.get(OTP_KEY).peekFirst();
		OtpEvent event = JSON.readValue(json, OtpEvent.class);
		assertThat(event.eventType()).isEqualTo(OtpEvent.OTP_GENERATE);
		assertThat(event.userId()).isEqualTo(42L);
		assertThat(json).doesNotContainIgnoringCase("password").doesNotContainIgnoringCase("code");
	}

	@Test
	void whenRedisIsDownAnOtpRequestFailsCleanlySoTheUserCanResend() {
		queue.failPushWith = new MessageBusException("Redis did not accept the message", new RuntimeException("down"));
		OtpProducerService producer = new OtpProducerService(new RedisMessageBus(queue, PROPERTIES), JSON, CLOCK);

		assertThatThrownBy(() -> producer.publishOtpGenerate(1L, "+263771234567")).isInstanceOf(OtpDispatchException.class);
	}

	@Test
	void aDecisionEventIsBestEffort_aRedisOutageIsReportedNotThrown() {
		DecisionEvent event = DecisionEvent.of(7L, 3L, "APPROVED", "TKO-1", CLOCK.instant());
		NotificationProducerService producer = new NotificationProducerService(new RedisMessageBus(queue, PROPERTIES), JSON);

		assertThat(producer.publishDecision(event)).isTrue();
		assertThat(queue.lists.get(NOTIFICATION_KEY)).hasSize(1);

		queue.failPushWith = new MessageBusException("Redis did not accept the message", new RuntimeException("down"));
		assertThat(producer.publishDecision(event)).isFalse();
	}

	// ------------------------------------------------------------------ consuming

	@Test
	void theWorkerHandsEachMessageToTheMatchingHandlerInTheOrderItWasSent() throws Exception {
		List<String> otp = new CopyOnWriteArrayList<>();
		List<String> notification = new CopyOnWriteArrayList<>();
		worker(body -> otp.add(new String(body, StandardCharsets.UTF_8)),
				body -> notification.add(new String(body, StandardCharsets.UTF_8)));
		RedisMessageBus bus = new RedisMessageBus(queue, PROPERTIES);
		bus.publish(MessageTopic.OTP, "first".getBytes(StandardCharsets.UTF_8));
		bus.publish(MessageTopic.OTP, "second".getBytes(StandardCharsets.UTF_8));
		bus.publish(MessageTopic.NOTIFICATION, "decided".getBytes(StandardCharsets.UTF_8));

		worker.start();

		await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
			assertThat(otp).containsExactly("first", "second");
			assertThat(notification).containsExactly("decided");
		});
		assertThat(worker.isRunning()).isTrue();
	}

	@Test
	void aMessageThatMakesItsHandlerThrowIsDroppedAndTheNextOneStillArrives() throws Exception {
		List<String> handled = new CopyOnWriteArrayList<>();
		worker(body -> {
			String text = new String(body, StandardCharsets.UTF_8);
			if (text.equals("poison")) {
				throw new IllegalStateException("cannot process this one");
			}
			handled.add(text);
		}, body -> {
		});
		RedisMessageBus bus = new RedisMessageBus(queue, PROPERTIES);
		bus.publish(MessageTopic.OTP, "poison".getBytes(StandardCharsets.UTF_8));
		bus.publish(MessageTopic.OTP, "healthy".getBytes(StandardCharsets.UTF_8));

		worker.start();

		await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> assertThat(handled).containsExactly("healthy"));
		assertThat(queue.lists.get(OTP_KEY)).isEmpty(); // the poison message did not come back
	}

	@Test
	void aRedisOutageDoesNotEndTheLoop_itKeepsTryingAndRecovers() throws Exception {
		List<String> handled = new CopyOnWriteArrayList<>();
		worker(body -> handled.add(new String(body, StandardCharsets.UTF_8)), body -> {
		});
		queue.failPopWith = new MessageBusException("connection lost", new RuntimeException("down"));
		worker.start();
		await().atMost(15, TimeUnit.SECONDS).until(() -> queue.poppedWhileDown.get() >= 1);

		queue.failPopWith = null; // Redis is back
		new RedisMessageBus(queue, PROPERTIES).publish(MessageTopic.OTP, "after-outage".getBytes(StandardCharsets.UTF_8));

		await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> assertThat(handled).containsExactly("after-outage"));
	}

	@Test
	void afterStopNothingIsConsumedAndStartingTwiceDoesNotDoubleUp() throws Exception {
		List<String> handled = new CopyOnWriteArrayList<>();
		worker(body -> handled.add(new String(body, StandardCharsets.UTF_8)), body -> {
		});
		worker.start();
		worker.start(); // idempotent
		RedisMessageBus bus = new RedisMessageBus(queue, PROPERTIES);
		bus.publish(MessageTopic.OTP, "one".getBytes(StandardCharsets.UTF_8));
		await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> assertThat(handled).containsExactly("one"));

		worker.stop();
		assertThat(worker.isRunning()).isFalse();
		bus.publish(MessageTopic.OTP, "two".getBytes(StandardCharsets.UTF_8));
		Thread.sleep(600);

		assertThat(handled).containsExactly("one");
		assertThat(queue.lists.get(OTP_KEY)).containsExactly("two"); // still waiting for the next start
	}
}
