package com.takeoff.backend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.takeoff.backend.dto.ChangePasswordRequest;
import com.takeoff.backend.dto.UserSummaryDto;
import com.takeoff.backend.security.TakeoffUserDetails;
import com.takeoff.backend.service.AccountService;

import jakarta.validation.Valid;

/**
 * A signed-in person's own account, for any role. {@code SecurityConfig} lets anyone who is authenticated call the
 * password change, including someone who has to replace a temporary password before doing anything else. The account
 * changed is always the token's own user (the id never comes from the request).
 */
@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

	private final AccountService accountService;

	public AccountController(AccountService accountService) {
		this.accountService = accountService;
	}

	@PutMapping("/password")
	public UserSummaryDto changePassword(@AuthenticationPrincipal TakeoffUserDetails principal,
			@Valid @RequestBody ChangePasswordRequest request) {
		return accountService.changePassword(principal.getId(), request);
	}
}
