package com.takeoff.backend.messaging;

/** The broker could not take the message. Wraps whatever the transport threw. */
public class MessageBusException extends RuntimeException {

	public MessageBusException(String message, Throwable cause) {
		super(message, cause);
	}
}
