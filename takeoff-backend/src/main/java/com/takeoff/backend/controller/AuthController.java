package com.takeoff.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.takeoff.backend.dto.JwtResponse;
import com.takeoff.backend.dto.LoginRequest;
import com.takeoff.backend.dto.OtpResendResponse;
import com.takeoff.backend.dto.OtpVerifyRequest;
import com.takeoff.backend.dto.RegistrationResponse;
import com.takeoff.backend.dto.ResendOtpRequest;
import com.takeoff.backend.dto.SignUpRequest;
import com.takeoff.backend.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody SignUpRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
	}

	@PostMapping("/verify-otp")
	public JwtResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
		return authService.verifyOtp(request);
	}

	@PostMapping("/resend-otp")
	public OtpResendResponse resendOtp(@Valid @RequestBody ResendOtpRequest request) {
		return authService.resendOtp(request);
	}

	@PostMapping("/login")
	public JwtResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}
}
