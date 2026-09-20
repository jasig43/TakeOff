package com.takeoff.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import com.takeoff.backend.config.TakeoffProperties;
import com.takeoff.backend.dto.JwtResponse;
import com.takeoff.backend.dto.LoginRequest;
import com.takeoff.backend.dto.OtpResendResponse;
import com.takeoff.backend.dto.OtpVerifyRequest;
import com.takeoff.backend.dto.RegistrationResponse;
import com.takeoff.backend.dto.ResendOtpRequest;
import com.takeoff.backend.dto.SignUpRequest;
import com.takeoff.backend.dto.UserSummaryDto;
import com.takeoff.backend.exception.ApiException;
import com.takeoff.backend.exception.OtpDispatchException;
import com.takeoff.backend.model.OtpToken;
import com.takeoff.backend.model.Role;
import com.takeoff.backend.model.User;
import com.takeoff.backend.repository.OtpTokenRepository;
import com.takeoff.backend.repository.UserRepository;
import com.takeoff.backend.security.JwtService;
import com.takeoff.backend.validation.PasswordPolicy;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private static final String INVALID_CREDENTIALS = "Invalid email or password.";
	private static final String RESEND_MESSAGE = "A new verification code is on its way.";

	private final UserRepository users;
	private final OtpTokenRepository otpTokens;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final OtpProducerService otpProducer;
	private final OtpCodec otpCodec;
	private final TransactionOperations transactions;
	private final TakeoffProperties.Otp otpSettings;
	private final Clock clock;
	/** Verified against when the email is unknown, so response time doesn't reveal which emails exist. */
	private final String timingDummyHash;

	public AuthService(UserRepository users, OtpTokenRepository otpTokens, PasswordEncoder passwordEncoder,
			JwtService jwtService, OtpProducerService otpProducer, OtpCodec otpCodec, TransactionOperations transactions,
			TakeoffProperties properties, Clock clock) {
		this.users = users;
		this.otpTokens = otpTokens;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.otpProducer = otpProducer;
		this.otpCodec = otpCodec;
		this.transactions = transactions;
		this.otpSettings = properties.otp();
		this.clock = clock;
		this.timingDummyHash = passwordEncoder.encode("timing-equalisation-only");
	}

	/**
	 * Creates an APPLICANT_DRIVER account and asks RabbitMQ to generate its OTP.
	 *
	 * <p>The account is committed first and the event is published afterwards: publishing inside the
	 * transaction could let the consumer run before the commit and not find the user. If the broker is
	 * down the account still exists and the response says {@code otpDispatched=false}; the client then
	 * offers "Resend code" (POST /auth/resend-otp), which is the documented recovery path.
	 */
	public RegistrationResponse register(SignUpRequest request) {
		User user = transactions.execute(status -> createApplicant(request));

		boolean dispatched = true;
		try {
			otpProducer.publishOtpGenerate(user.getId(), user.getPhoneNumber());
		}
		catch (OtpDispatchException ex) {
			dispatched = false;
			log.warn("Account {} created but its OTP event was not queued", user.getId());
		}

		String message = dispatched ? "Account created. We've sent a 6-digit verification code to your phone."
				: "Account created, but we couldn't send your verification code. Use \"Resend code\" to try again.";
		return new RegistrationResponse(user.getEmail(), maskPhone(user.getPhoneNumber()),
				otpSettings.expirationSeconds(), dispatched, message);
	}

	private User createApplicant(SignUpRequest request) {
		String email = normalizeEmail(request.email());
		String phone = request.phoneNumber().trim();

		if (users.existsByEmail(email)) {
			throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
					"An account with this email address already exists.");
		}
		if (users.existsByPhoneNumber(phone)) {
			throw new ApiException(HttpStatus.CONFLICT, "PHONE_ALREADY_REGISTERED",
					"An account with this phone number already exists.");
		}

		// Role is fixed here, never taken from the request.
		User user = new User(request.fullName().trim(), email, phone, passwordEncoder.encode(request.password()),
				Role.APPLICANT_DRIVER);
		return users.saveAndFlush(user);
	}

	/**
	 * Validates the OTP, marks the phone verified and returns a JWT.
	 *
	 * <p>{@code noRollbackFor}: a wrong guess throws, but the incremented attempt counter (and burning of an
	 * exhausted token) must still be committed or the attempt limit would be meaningless.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public JwtResponse verifyOtp(OtpVerifyRequest request) {
		User user = findByIdentifier(request.identifier())
			.filter(candidate -> candidate.getRole() == Role.APPLICANT_DRIVER)
			.orElseThrow(AuthService::invalidOtp);
		OtpToken token = otpTokens.findFirstByUserIdOrderByIdDesc(user.getId()).orElseThrow(AuthService::invalidOtp);

		if (token.isConsumed()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_ALREADY_USED",
					"This code has already been used or replaced. Request a new code.");
		}
		if (!token.getExpiresAt().isAfter(clock.instant())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_EXPIRED", "This code has expired. Request a new one.");
		}

		if (!otpCodec.matches(user.getId(), request.otp(), token.getCode())) {
			token.setAttempts(token.getAttempts() + 1);
			boolean exhausted = token.getAttempts() >= otpSettings.maxAttempts();
			if (exhausted) {
				token.setConsumed(true);
			}
			otpTokens.save(token);
			throw exhausted
					? new ApiException(HttpStatus.BAD_REQUEST, "OTP_TOO_MANY_ATTEMPTS",
							"Too many incorrect attempts. Request a new code.")
					: invalidOtp();
		}

		token.setConsumed(true);
		otpTokens.save(token);
		user.setPhoneVerified(true);
		users.save(user);
		return issueToken(user);
	}

	/** Asks for a fresh OTP. The response is identical for unknown and already-verified accounts. */
	public OtpResendResponse resendOtp(ResendOtpRequest request) {
		Optional<User> found = findByIdentifier(request.identifier())
			.filter(user -> user.getRole() == Role.APPLICANT_DRIVER && !user.isPhoneVerified());
		if (found.isEmpty()) {
			return new OtpResendResponse(otpSettings.expirationSeconds(), true, RESEND_MESSAGE);
		}

		User user = found.get();
		otpTokens.findFirstByUserIdOrderByIdDesc(user.getId()).ifPresent(latest -> {
			long waitSeconds = otpSettings.resendCooldownSeconds()
					- Duration.between(latest.getCreatedAt(), clock.instant()).toSeconds();
			if (waitSeconds > 0) {
				throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_RESEND_TOO_SOON",
						"Please wait " + waitSeconds + " seconds before requesting another code.");
			}
		});

		otpProducer.publishOtpGenerate(user.getId(), user.getPhoneNumber()); // OtpDispatchException -> 503
		return new OtpResendResponse(otpSettings.expirationSeconds(), true, RESEND_MESSAGE);
	}

	public JwtResponse login(LoginRequest request) {
		Optional<User> found = users.findByEmail(normalizeEmail(request.email()));

		// Always run one BCrypt comparison so unknown emails cost the same as wrong passwords.
		String hash = found.map(User::getPasswordHash).orElse(timingDummyHash);
		boolean passwordMatches = !PasswordPolicy.exceedsMaxBytes(request.password())
				&& passwordEncoder.matches(request.password(), hash);

		if (found.isEmpty() || !passwordMatches || !found.get().isEnabled()) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", INVALID_CREDENTIALS);
		}

		User user = found.get();
		if (user.getRole() == Role.APPLICANT_DRIVER && !user.isPhoneVerified()) {
			// Only reachable with the correct password, so this reveals nothing to a guesser.
			throw new ApiException(HttpStatus.FORBIDDEN, "PHONE_NOT_VERIFIED",
					"Verify your phone number to finish signing in.");
		}
		return issueToken(user);
	}

	private JwtResponse issueToken(User user) {
		return JwtResponse.bearer(jwtService.generateToken(user), jwtService.lifetimeSeconds(),
				UserSummaryDto.from(user));
	}

	private Optional<User> findByIdentifier(String identifier) {
		String value = identifier.trim();
		return value.contains("@") ? users.findByEmail(normalizeEmail(value)) : users.findByPhoneNumber(value);
	}

	private static ApiException invalidOtp() {
		return new ApiException(HttpStatus.BAD_REQUEST, "OTP_INVALID",
				"That code isn't right. Check the digits and try again.");
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	/** "+15550199" -> "+••••0199". */
	static String maskPhone(String phone) {
		if (phone.length() <= 5) {
			return phone;
		}
		return phone.charAt(0) + "•".repeat(phone.length() - 5) + phone.substring(phone.length() - 4);
	}
}
