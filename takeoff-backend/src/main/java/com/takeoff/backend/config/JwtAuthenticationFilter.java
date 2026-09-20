package com.takeoff.backend.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.takeoff.backend.security.CustomUserDetailsService;
import com.takeoff.backend.security.JwtService;
import com.takeoff.backend.security.TakeoffUserDetails;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates requests carrying {@code Authorization: Bearer <jwt>}. An invalid or expired token
 * simply leaves the request unauthenticated; the security chain's entry point then answers 401 for
 * protected routes. The token's subject must match the stored user and the account must still be
 * enabled, so disabling a user takes effect immediately rather than at token expiry.
 *
 * <p>Not a Spring bean on purpose: it is instantiated inside {@link SecurityConfig} so that Boot does
 * not additionally register it as a global servlet filter.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;
	private final CustomUserDetailsService userDetailsService;

	public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {
		this.jwtService = jwtService;
		this.userDetailsService = userDetailsService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.startsWith(BEARER_PREFIX)
				&& SecurityContextHolder.getContext().getAuthentication() == null) {
			authenticate(header.substring(BEARER_PREFIX.length()).trim(), request);
		}
		chain.doFilter(request, response);
	}

	private void authenticate(String token, HttpServletRequest request) {
		try {
			Jwt jwt = jwtService.parse(token);
			String email = jwt.getClaimAsString("email");
			if (email == null) {
				return;
			}
			TakeoffUserDetails user = userDetailsService.loadUserByUsername(email);
			if (!user.isEnabled() || !String.valueOf(user.getId()).equals(jwt.getSubject())) {
				return;
			}
			UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken
				.authenticated(user, null, user.getAuthorities());
			authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
			SecurityContext context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(authentication);
			SecurityContextHolder.setContext(context);
		}
		catch (JwtException | UsernameNotFoundException ex) {
			// Never log the token itself.
			log.debug("Rejected bearer token: {}", ex.getMessage());
		}
	}
}
