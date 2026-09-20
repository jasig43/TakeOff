package com.takeoff.backend.security;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.takeoff.backend.repository.UserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

	private final UserRepository users;

	public CustomUserDetailsService(UserRepository users) {
		this.users = users;
	}

	@Override
	public TakeoffUserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		return users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
			.map(TakeoffUserDetails::new)
			.orElseThrow(() -> new UsernameNotFoundException("No user with that email"));
	}
}
