package com.takeoff.backend.sms;

/**
 * Delivers a text message to a phone number. Implementations are chosen by {@code takeoff.sms.provider};
 * adding another provider means adding one class and one case in {@code SmsConfig}.
 */
public interface SmsSender {

	/**
	 * @param toE164 destination in E.164 format, e.g. {@code +263771234567}
	 * @param body   message text
	 * @throws SmsDeliveryException if the provider rejects the message or cannot be reached
	 */
	void send(String toE164, String body);
}
