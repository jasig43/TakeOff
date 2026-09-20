package com.takeoff.backend.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.takeoff.backend.model.User;

/** Authenticated principal: carries the user id so controllers never need to look users up by name. */
public class TakeoffUserDetails implements UserDetails {

	private final Long id;
	private final String email;
	private final String passwordHash;
	private final boolean enabled;
	private final List<GrantedAuthority> authorities;

	public TakeoffUserDetails(User user) {
		this.id = user.getId();
		this.email = user.getEmail();
		this.passwordHash = user.getPasswordHash();
		this.enabled = user.isEnabled();
		this.authorities = List.of(new SimpleGrantedAuthority(user.getRole().authority()));
	}

	public Long getId() {
		return id;
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
