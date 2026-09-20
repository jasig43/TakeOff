package com.takeoff.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.takeoff.backend.dto.ChangePasswordRequest;
import com.takeoff.backend.security.TakeoffUserDetails;
import com.takeoff.backend.service.AccountService;

import jakarta.validation.Valid;

/**
 * The administrator's own account settings. {@code SecurityConfig} requires ROLE_LOGISTICS_ADMIN for
 * {@code /api/v1/admin/**}, and the account changed is always the authenticated user's own (the id comes from the
 * token, never from the request).
 */
@RestController
@RequestMapping("/api/v1/admin/account")
public class AdminAccountController {

	private final AccountService accountService;

	public AdminAccountController(AccountService accountService) {
		this.accountService = accountService;
	}

	@PutMapping("/password")
	public ResponseEntity<Void> changePassword(@AuthenticationPrincipal TakeoffUserDetails principal,
			@Valid @RequestBody ChangePasswordRequest request) {
		accountService.changePassword(principal.getId(), request);
		return ResponseEntity.noContent().build();
	}
}
