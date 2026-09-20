package com.takeoff.backend.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class LoggingSmsSender implements SmsSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

	private final boolean devOrTest;

	public LoggingSmsSender(boolean devOrTest) {
		this.devOrTest = devOrTest;
		if (!devOrTest) {
			log.warn("takeoff.sms.provider is 'none': verification codes will NOT be delivered to users. "
					+ "Set SMS_PROVIDER=twilio and the TWILIO_* variables to send real SMS.");
		}
	}

	@Override
	public void send(String toE164, String body) {
		if (devOrTest) {
			log.debug("SMS provider is 'none': not sending an SMS (dev/test).");
		}
		else {
			log.warn("SMS provider is 'none': a verification code was NOT delivered.");
		}
	}
}
