package com.takeoff.backend.messaging;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;

/**
 * A Redis list reached through Lettuce (LPUSH to add, BRPOP to take). Used only when
 * {@code takeoff.messaging.provider=redis}. Publishing shares one connection; every thread that pops gets its own,
 * because a blocking pop occupies its connection for the whole wait. Lettuce reconnects on its own after an outage.
 *
 * <p>Delivery is at-most-once: a message is gone from Redis the moment it is popped. That is acceptable here because a
 * lost OTP event is recovered by the user pressing "Resend code", and the decision notification already exists in the
 * database before its event is sent.
 */
@Component
@ConditionalOnProperty(name = "takeoff.messaging.provider", havingValue = "redis")
public class LettuceListQueue implements ListQueue, DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(LettuceListQueue.class);

	private final RedisClient client;
	private final StatefulRedisConnection<String, String> publishing;
	private final ConcurrentMap<Thread, StatefulRedisConnection<String, String>> blocking = new ConcurrentHashMap<>();

	public LettuceListQueue(@Value("${takeoff.redis.url}") String url) {
		RedisURI uri = RedisURI.create(url);
		uri.setTimeout(Duration.ofSeconds(10));
		this.client = RedisClient.create(uri);
		this.publishing = client.connect(); // fails fast at startup when the URL is wrong or Redis is unreachable
		log.info("Connected to Redis at {}:{} for the message queue", uri.getHost(), uri.getPort());
	}

	@Override
	public void push(String key, String value) {
		try {
			publishing.sync().lpush(key, value);
		}
		catch (RedisException ex) {
			throw new MessageBusException("Redis did not accept the message: " + ex.getMessage(), ex);
		}
	}

	@Override
	public String pop(String key, Duration timeout) {
		StatefulRedisConnection<String, String> connection = blocking.computeIfAbsent(Thread.currentThread(),
				thread -> client.connect());
		KeyValue<String, String> popped = connection.sync().brpop(Math.max(1, timeout.toSeconds()), key);
		return popped == null || !popped.hasValue() ? null : popped.getValue();
	}

	@Override
	public void destroy() {
		blocking.values().forEach(StatefulRedisConnection::close);
		publishing.close();
		client.shutdown();
	}
}
