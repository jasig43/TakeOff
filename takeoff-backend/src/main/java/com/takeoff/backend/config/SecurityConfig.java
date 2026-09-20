package com.takeoff.backend.config;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.takeoff.backend.exception.ApiErrorResponse;
import com.takeoff.backend.model.Role;
import com.takeoff.backend.security.CustomUserDetailsService;
import com.takeoff.backend.security.JwtService;
import com.takeoff.backend.security.TakeoffUserDetails;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Stateless JWT security. Public: {@code /api/v1/auth/**}. Everything else needs a valid bearer
 * token, and the driver / admin areas additionally require their role. This is the authoritative
 * RBAC layer; the frontend's route guards are only a convenience.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
			CustomUserDetailsService userDetailsService, ObjectMapper objectMapper, Clock clock) throws Exception {
		AuthenticationEntryPoint entryPoint = (request, response, ex) -> writeError(objectMapper, request, response,
				HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required to access this resource.");
		AccessDeniedHandler deniedHandler = (request, response, ex) -> {
			if (holdsOnlyPasswordChangeAuthority()) {
				writeError(objectMapper, request, response, HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED",
						"You must choose a new password before you can continue.");
				return;
			}
			writeError(objectMapper, request, response, HttpStatus.FORBIDDEN, "FORBIDDEN",
					"You do not have permission to access this resource.");
		};

		http
			.csrf(AbstractHttpConfigurer::disable) // stateless bearer-token API: no cookies, so no CSRF surface
			.cors(Customizer.withDefaults())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
				.requestMatchers("/api/v1/auth/**").permitAll()
				.requestMatchers(HttpMethod.GET, "/", "/api/v1/health").permitAll() // the address itself, and the host's liveness probe
				// Any signed-in person may change their own password, including one who must do so before anything else.
				.requestMatchers(HttpMethod.PUT, "/api/v1/account/password").authenticated()
				.requestMatchers("/api/v1/drivers/**").hasRole(Role.APPLICANT_DRIVER.name())
				.requestMatchers("/api/v1/admin/**").hasRole(Role.LOGISTICS_ADMIN.name())
				// Anything else needs a real role, which someone still on a temporary password does not hold.
				.anyRequest().hasAnyRole(Role.APPLICANT_DRIVER.name(), Role.LOGISTICS_ADMIN.name()))
			.exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint)
				.accessDeniedHandler(deniedHandler))
			.addFilterBefore(new JwtAuthenticationFilter(jwtService, userDetailsService, clock),
					UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(TakeoffProperties properties) {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(properties.cors().allowedOrigins());
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
		cors.setAllowCredentials(false); // bearer tokens travel in a header, not in cookies
		cors.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", cors);
		return source;
	}

	private static boolean holdsOnlyPasswordChangeAuthority() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication != null && authentication.getAuthorities().stream()
			.anyMatch(authority -> TakeoffUserDetails.PASSWORD_CHANGE_REQUIRED.equals(authority.getAuthority()));
	}

	private static void writeError(ObjectMapper objectMapper, HttpServletRequest request, HttpServletResponse response,
			HttpStatus status, String code, String message) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getOutputStream(),
				ApiErrorResponse.of(status, code, message, request.getRequestURI()));
	}
}
