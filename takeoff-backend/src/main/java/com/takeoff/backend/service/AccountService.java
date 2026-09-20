package com.takeoff.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.takeoff.backend.dto.ChangePasswordRequest;
import com.takeoff.backend.dto.UserSummaryDto;
import com.takeoff.backend.exception.ApiException;
import com.takeoff.backend.exception.FieldValidationException;
import com.takeoff.backend.model.User;
import com.takeoff.backend.repository.UserRepository;

/** Things a signed-in user does to their own account, whatever their role. */
@Service
public class AccountService {

	private static final Logger log = LoggerFactory.getLogger(AccountService.class);

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;

	public AccountService(UserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	/**
	 * Changes the password after checking the current one. If the current one was a temporary password issued by an
	 * administrator, this is also how the person leaves the "must change password" state.
	 *
	 * <p>A wrong current password is reported as a field error (400), not 401: a 401 would make the SPA treat the
	 * session as expired and sign the person out, and they are in fact still properly signed in.
	 * Tokens already issued stay valid until they expire (JWTs are stateless), which the README notes.
	 *
	 * @return the refreshed account summary, so the client can drop its "must change password" flag
	 */
	@Transactional
	public UserSummaryDto changePassword(Long userId, ChangePasswordRequest request) {
		User user = users.findById(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Account not found."));

		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw new FieldValidationException("currentPassword", "Your current password is not correct.");
		}
		if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
			throw new FieldValidationException("newPassword", "Choose a password that is different from your current one.");
		}

		user.changePassword(passwordEncoder.encode(request.newPassword()));
		users.save(user);
		log.info("User {} changed their password", user.getId());
		return UserSummaryDto.from(user);
	}
}
