package com.takeoff.backend.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.takeoff.backend.dto.AdminDtos.PageResponse;
import com.takeoff.backend.dto.UserAdminDtos.AdminUserDto;
import com.takeoff.backend.dto.UserAdminDtos.ChangeRoleRequest;
import com.takeoff.backend.dto.UserAdminDtos.CreateAccountRequest;
import com.takeoff.backend.dto.UserAdminDtos.IssuedCredentialDto;
import com.takeoff.backend.security.TakeoffUserDetails;
import com.takeoff.backend.service.AdminUserService;

import jakarta.validation.Valid;

/**
 * User and role management for administrators. {@code SecurityConfig} requires ROLE_LOGISTICS_ADMIN for
 * {@code /api/v1/admin/**}. Responses that carry a temporary password are marked {@code no-store}.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

	private final AdminUserService service;

	public AdminUserController(AdminUserService service) {
		this.service = service;
	}

	@GetMapping
	public PageResponse<AdminUserDto> list(@RequestParam(name = "q", required = false) String query,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
		return service.list(query, page, size);
	}

	@PostMapping
	public ResponseEntity<IssuedCredentialDto> create(@AuthenticationPrincipal TakeoffUserDetails principal,
			@Valid @RequestBody CreateAccountRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.cacheControl(CacheControl.noStore())
			.body(service.create(principal.getId(), request));
	}

	@PatchMapping("/{id}/role")
	public AdminUserDto changeRole(@AuthenticationPrincipal TakeoffUserDetails principal, @PathVariable Long id,
			@Valid @RequestBody ChangeRoleRequest request) {
		return service.changeRole(principal.getId(), id, request.role());
	}

	@PostMapping("/{id}/temporary-password")
	public ResponseEntity<IssuedCredentialDto> resetPassword(@AuthenticationPrincipal TakeoffUserDetails principal,
			@PathVariable Long id) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.resetPassword(principal.getId(), id));
	}
}
