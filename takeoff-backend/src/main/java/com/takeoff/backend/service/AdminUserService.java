package com.takeoff.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.takeoff.backend.dto.AdminDtos.PageResponse;
import com.takeoff.backend.dto.UserAdminDtos.AdminUserDto;
import com.takeoff.backend.dto.UserAdminDtos.CreateAccountRequest;
import com.takeoff.backend.dto.UserAdminDtos.IssuedCredentialDto;
import com.takeoff.backend.exception.ApiException;
import com.takeoff.backend.model.Role;
import com.takeoff.backend.model.User;
import com.takeoff.backend.repository.UserRepository;

/**
 * An administrator managing accounts: list them, create one with a temporary password, assign a role, and issue a new
 * temporary password. The security chain guarantees the caller is an administrator; the caller's id is passed in so
 * the rules about acting on oneself can be enforced.
 *
 * <p>Temporary passwords are generated here, returned once, stored only as a BCrypt hash, and never logged. The person
 * must replace it at first sign-in ({@code mustChangePassword}) and it stops working after
 * {@code takeoff.accounts.temporary-password-hours} (default 72).
 */
@Service
public class AdminUserService {

	private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
	private static final int MAX_PAGE_SIZE = 50;

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final TemporaryPasswordGenerator generator;
	private final Clock clock;
	private final Duration temporaryPasswordLifetime;

	public AdminUserService(UserRepository users, PasswordEncoder passwordEncoder, TemporaryPasswordGenerator generator,
			Clock clock, @Value("${takeoff.accounts.temporary-password-hours:72}") long temporaryPasswordHours) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.generator = generator;
		this.clock = clock;
		this.temporaryPasswordLifetime = Duration.ofHours(Math.max(temporaryPasswordHours, 1));
	}

	@Transactional(readOnly = true)
	public PageResponse<AdminUserDto> list(String query, int page, int size) {
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		int safePage = Math.max(page, 0);
		String pattern = "%" + (query == null ? "" : escapeLike(query.trim().toLowerCase(Locale.ROOT))) + "%";

		Page<User> result = users.search(pattern,
				PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
		List<AdminUserDto> items = result.getContent().stream().map(AdminUserDto::from).toList();
		return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(),
				result.getTotalPages());
	}

	@Transactional
	public IssuedCredentialDto create(Long adminId, CreateAccountRequest request) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		String phone = request.phoneNumber().trim();
		if (users.existsByEmail(email)) {
			throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
					"An account with this email address already exists.");
		}
		if (users.existsByPhoneNumber(phone)) {
			throw new ApiException(HttpStatus.CONFLICT, "PHONE_ALREADY_REGISTERED",
					"An account with this phone number already exists.");
		}

		String temporaryPassword = generator.generate();
		Instant expiresAt = clock.instant().plus(temporaryPasswordLifetime);
		User user = new User(request.fullName().trim(), email, phone, "pending", request.role());
		user.issueTemporaryPassword(passwordEncoder.encode(temporaryPassword), expiresAt);
		user.setPhoneVerified(true); // the administrator vouches for the person; they skip the sign-up OTP step
		User saved = users.saveAndFlush(user);

		log.info("Administrator {} created account {} with role {}", adminId, saved.getId(), saved.getRole());
		return new IssuedCredentialDto(AdminUserDto.from(saved), temporaryPassword, expiresAt);
	}

	@Transactional
	public AdminUserDto changeRole(Long adminId, Long userId, Role role) {
		User user = find(userId);
		if (user.getId().equals(adminId)) {
			throw new ApiException(HttpStatus.CONFLICT, "CANNOT_CHANGE_OWN_ROLE",
					"You cannot change your own role. Ask another administrator to do it.");
		}
		if (user.getRole() != role) {
			Role previous = user.getRole();
			user.setRole(role);
			users.save(user);
			log.info("Administrator {} changed the role of account {} from {} to {}", adminId, userId, previous, role);
		}
		return AdminUserDto.from(user);
	}

	@Transactional
	public IssuedCredentialDto resetPassword(Long adminId, Long userId) {
		User user = find(userId);
		if (user.getId().equals(adminId)) {
			throw new ApiException(HttpStatus.CONFLICT, "CANNOT_RESET_OWN_PASSWORD",
					"Use Change password in your own settings instead.");
		}

		String temporaryPassword = generator.generate();
		Instant expiresAt = clock.instant().plus(temporaryPasswordLifetime);
		user.issueTemporaryPassword(passwordEncoder.encode(temporaryPassword), expiresAt);
		users.save(user);

		log.info("Administrator {} issued a new temporary password for account {}", adminId, userId);
		return new IssuedCredentialDto(AdminUserDto.from(user), temporaryPassword, expiresAt);
	}

	private User find(Long userId) {
		return users.findById(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Account not found."));
	}

	private static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
