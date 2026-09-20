package com.takeoff.backend.sms;

/**
 * An SMS could not be delivered. The message is safe to log: it never contains the message body (which holds the
 * one-time code), the destination number or any credential.
 */
public class SmsDeliveryException extends RuntimeException {

	public SmsDeliveryException(String message) {
		super(message);
	}

	public SmsDeliveryException(String message, Throwable cause) {
		super(message, cause);
	}
}
