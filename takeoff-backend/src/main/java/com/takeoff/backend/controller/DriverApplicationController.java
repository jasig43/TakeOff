package com.takeoff.backend.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.takeoff.backend.dto.ApplicationDto;
import com.takeoff.backend.dto.IdentityRequest;
import com.takeoff.backend.dto.NotificationDto;
import com.takeoff.backend.dto.PersonalDetailsRequest;
import com.takeoff.backend.dto.VehicleRequest;
import com.takeoff.backend.model.DocumentType;
import com.takeoff.backend.security.TakeoffUserDetails;
import com.takeoff.backend.service.ApplicationService;
import com.takeoff.backend.service.NotificationService;

import jakarta.validation.Valid;

/**
 * Everything a driver does with their own application and notifications. {@code SecurityConfig} requires
 * ROLE_APPLICANT_DRIVER for {@code /api/v1/drivers/**}, and every call is scoped to the authenticated user's id, so
 * there is no way to address another driver's data.
 */
@RestController
@RequestMapping("/api/v1/drivers")
public class DriverApplicationController {

	private final ApplicationService applicationService;
	private final NotificationService notificationService;

	public DriverApplicationController(ApplicationService applicationService, NotificationService notificationService) {
		this.applicationService = applicationService;
		this.notificationService = notificationService;
	}

	@GetMapping("/application")
	public ApplicationDto application(@AuthenticationPrincipal TakeoffUserDetails principal) {
		return applicationService.get(principal.getId());
	}

	@PutMapping("/application/personal")
	public ApplicationDto savePersonal(@AuthenticationPrincipal TakeoffUserDetails principal,
			@Valid @RequestBody PersonalDetailsRequest request) {
		return applicationService.savePersonal(principal.getId(), request);
	}

	@PutMapping("/application/identity")
	public ApplicationDto saveIdentity(@AuthenticationPrincipal TakeoffUserDetails principal,
			@Valid @RequestBody IdentityRequest request) {
		return applicationService.saveIdentity(principal.getId(), request);
	}

	@PutMapping("/application/vehicle")
	public ApplicationDto saveVehicle(@AuthenticationPrincipal TakeoffUserDetails principal,
			@Valid @RequestBody VehicleRequest request) {
		return applicationService.saveVehicle(principal.getId(), request);
	}

	@PostMapping(path = "/application/documents/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApplicationDto uploadDocument(@AuthenticationPrincipal TakeoffUserDetails principal,
			@PathVariable DocumentType type, @RequestParam("file") MultipartFile file) {
		return applicationService.uploadDocument(principal.getId(), type, file);
	}

	@GetMapping("/application/documents/{type}")
	public ResponseEntity<Resource> downloadDocument(@AuthenticationPrincipal TakeoffUserDetails principal,
			@PathVariable DocumentType type) {
		return DocumentResponses.inline(applicationService.openDocument(principal.getId(), type));
	}

	@DeleteMapping("/application/documents/{type}")
	public ApplicationDto deleteDocument(@AuthenticationPrincipal TakeoffUserDetails principal,
			@PathVariable DocumentType type) {
		return applicationService.deleteDocument(principal.getId(), type);
	}

	@PostMapping("/application/submit")
	public ApplicationDto submit(@AuthenticationPrincipal TakeoffUserDetails principal) {
		return applicationService.submit(principal.getId());
	}

	// ---- notifications

	@GetMapping("/notifications")
	public NotificationDto.Inbox notifications(@AuthenticationPrincipal TakeoffUserDetails principal) {
		return notificationService.inbox(principal.getId());
	}

	@PostMapping("/notifications/{id}/read")
	public NotificationDto.Inbox markRead(@AuthenticationPrincipal TakeoffUserDetails principal, @PathVariable Long id) {
		notificationService.markRead(principal.getId(), id);
		return notificationService.inbox(principal.getId());
	}

	@PostMapping("/notifications/read-all")
	public NotificationDto.Inbox markAllRead(@AuthenticationPrincipal TakeoffUserDetails principal) {
		notificationService.markAllRead(principal.getId());
		return notificationService.inbox(principal.getId());
	}
}
