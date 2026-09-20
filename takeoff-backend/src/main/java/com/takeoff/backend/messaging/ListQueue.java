package com.takeoff.backend.messaging;

import java.time.Duration;

/**
 * The small slice of a Redis list the Redis transport needs: push at one end, blocking pop from the other. A separate
 * interface so the transport and its worker can be tested without a Redis server.
 */
public interface ListQueue {

	/** Adds a value to the queue. */
	void push(String key, String value);

	/** Removes the oldest value, waiting up to {@code timeout} for one to arrive; null when none did. */
	String pop(String key, Duration timeout);
}
