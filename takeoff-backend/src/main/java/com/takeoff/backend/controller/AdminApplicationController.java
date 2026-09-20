package com.takeoff.backend.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.takeoff.backend.dto.AdminDtos.ApplicationDetail;
import com.takeoff.backend.dto.AdminDtos.ApplicationSummary;
import com.takeoff.backend.dto.AdminDtos.PageResponse;
import com.takeoff.backend.dto.AdminDtos.Summary;
import com.takeoff.backend.dto.DecisionRequest;
import com.takeoff.backend.model.ApplicationStatus;
import com.takeoff.backend.model.DocumentType;
import com.takeoff.backend.security.TakeoffUserDetails;
import com.takeoff.backend.service.AdminApplicationService;

import jakarta.validation.Valid;

/** The administrator's review workflow. {@code SecurityConfig} requires ROLE_LOGISTICS_ADMIN for {@code /api/v1/admin/**}. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminApplicationController {

	private final AdminApplicationService service;

	public AdminApplicationController(AdminApplicationService service) {
		this.service = service;
	}

	@GetMapping("/summary")
	public Summary summary() {
		return service.summary();
	}

	@GetMapping("/applications")
	public PageResponse<ApplicationSummary> applications(@RequestParam(required = false) ApplicationStatus status,
			@RequestParam(name = "q", required = false) String query, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return service.search(status, query, page, size);
	}

	@GetMapping("/applications/{id}")
	public ApplicationDetail application(@PathVariable Long id) {
		return service.detail(id);
	}

	@GetMapping("/applications/{id}/documents/{type}")
	public ResponseEntity<Resource> document(@PathVariable Long id, @PathVariable DocumentType type) {
		return DocumentResponses.inline(service.openDocument(id, type));
	}

	@PatchMapping("/applications/{id}/status")
	public ApplicationDetail decide(@AuthenticationPrincipal TakeoffUserDetails principal, @PathVariable Long id,
			@Valid @RequestBody DecisionRequest request) {
		return service.decide(principal.getId(), id, request);
	}
}
