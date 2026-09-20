package com.takeoff.backend.sms;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.takeoff.backend.config.TakeoffProperties;

/**
 * Sends SMS through Twilio's REST API ({@code POST /2010-04-01/Accounts/{sid}/Messages.json}) using plain
 * HTTPS and Basic auth, so no vendor SDK is needed.
 *
 * <p>Failures are reported as {@link SmsDeliveryException} carrying only the HTTP status and Twilio's numeric error
 * code. Twilio's error text is deliberately not propagated because it can contain the destination number.
 */
public class TwilioSmsSender implements SmsSender {

	private static final Pattern ERROR_CODE = Pattern.compile("\"code\"\\s*:\\s*(\\d+)");

	private final RestClient client;
	private final String accountSid;
	private final String fromNumber;
	private final String messagingServiceSid;

	/**
	 * @param builder a RestClient builder; the caller owns HTTP timeouts (see {@code SmsConfig}) so tests can
	 *                substitute a mock server
	 */
	public TwilioSmsSender(TakeoffProperties.Twilio config, RestClient.Builder builder) {
		requireConfigured(config);
		this.accountSid = config.accountSid().trim();
		this.fromNumber = isBlank(config.fromNumber()) ? null : config.fromNumber().trim();
		this.messagingServiceSid = isBlank(config.messagingServiceSid()) ? null : config.messagingServiceSid().trim();

		String baseUrl = isBlank(config.apiBaseUrl()) ? "https://api.twilio.com" : config.apiBaseUrl().trim();
		this.client = builder.baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
			.defaultHeaders(headers -> headers.setBasicAuth(this.accountSid, config.authToken().trim()))
			.build();
	}

	/**
	 * Fails fast with an actionable message when credentials or the sender are missing. Public so the configuration
	 * can call it before it builds an HTTP client, keeping the error clear.
	 */
	public static void requireConfigured(TakeoffProperties.Twilio config) {
		if (isBlank(config.accountSid()) || isBlank(config.authToken())) {
			throw new IllegalStateException("takeoff.sms.provider=twilio requires TWILIO_ACCOUNT_SID and "
					+ "TWILIO_AUTH_TOKEN.");
		}
		if (isBlank(config.fromNumber()) && isBlank(config.messagingServiceSid())) {
			throw new IllegalStateException("takeoff.sms.provider=twilio requires either TWILIO_FROM_NUMBER or "
					+ "TWILIO_MESSAGING_SERVICE_SID.");
		}
	}

	@Override
	public void send(String toE164, String body) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("To", toE164);
		if (messagingServiceSid != null) {
			form.add("MessagingServiceSid", messagingServiceSid);
		}
		else {
			form.add("From", fromNumber);
		}
		form.add("Body", body);

		try {
			client.post()
				.uri("/2010-04-01/Accounts/{sid}/Messages.json", accountSid)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.toBodilessEntity();
		}
		catch (RestClientResponseException ex) {
			Matcher code = ERROR_CODE.matcher(ex.getResponseBodyAsString());
			throw new SmsDeliveryException("Twilio rejected the message (HTTP " + ex.getStatusCode().value()
					+ (code.find() ? ", Twilio error " + code.group(1) : "") + ")");
		}
		catch (RestClientException ex) {
			throw new SmsDeliveryException("Could not reach Twilio (" + ex.getClass().getSimpleName() + ")", ex);
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
