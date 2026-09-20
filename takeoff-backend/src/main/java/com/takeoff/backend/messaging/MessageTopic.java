package com.takeoff.backend.messaging;

/** The two kinds of event the application sends. */
public enum MessageTopic {

	/** "Generate a one-time code for this user" (OTP_GENERATE). */
	OTP,

	/** "An application was approved or rejected" (APPLICATION_DECIDED). */
	NOTIFICATION
}
