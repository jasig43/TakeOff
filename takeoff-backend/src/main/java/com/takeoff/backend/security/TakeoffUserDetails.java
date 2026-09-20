package com.takeoff.backend.security;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.takeoff.backend.model.User;

/**
 * Authenticated principal: carries the user id so controllers never need to look users up by name.
 *
 * <p>The role is read from the database on every request, so a role change takes effect immediately. While a
 * temporary password is still in force the person holds no role at all, only {@link #PASSWORD_CHANGE_REQUIRED}, so
 * every role-protected route refuses them until they choose a password of their own.
 */
public class TakeoffUserDetails implements UserDetails {

	/** The only authority held while a temporary password is in force. */
	public static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";

	private final Long id;
	private final String email;
	private final String passwordHash;
	private final boolean enabled;
	private final boolean mustChangePassword;
	private final Instant temporaryPasswordExpiresAt;
	private final List<GrantedAuthority> authorities;

	public TakeoffUserDetails(User user) {
		this.id = user.getId();
		this.email = user.getEmail();
		this.passwordHash = user.getPasswordHash();
		this.enabled = user.isEnabled();
		this.mustChangePassword = user.isMustChangePassword();
		this.temporaryPasswordExpiresAt = user.getTemporaryPasswordExpiresAt();
		this.authorities = mustChangePassword ? List.of(new SimpleGrantedAuthority(PASSWORD_CHANGE_REQUIRED))
				: List.of(new SimpleGrantedAuthority(user.getRole().authority()));
	}

	public Long getId() {
		return id;
	}

	public boolean isMustChangePassword() {
		return mustChangePassword;
	}

	/** True when the password is a temporary one whose time has run out; such a person is treated as signed out. */
	public boolean temporaryPasswordExpired(Instant now) {
		return mustChangePassword && temporaryPasswordExpiresAt != null && !temporaryPasswordExpiresAt.isAfter(now);
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return authorities;
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}
}
