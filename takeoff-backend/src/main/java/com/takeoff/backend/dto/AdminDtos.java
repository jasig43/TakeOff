package com.takeoff.backend.dto;

import java.time.Instant;
import java.util.List;

import com.takeoff.backend.model.ApplicationStatus;

/** Response shapes used only by the administrator endpoints. */
public final class AdminDtos {

	private AdminDtos() {
	}

	/** One row of the review queue. */
	public record ApplicationSummary(Long id, String referenceId, ApplicationStatus status, String driverName,
			String driverEmail, String driverPhone, String plateNumber, Instant submittedAt, Instant decidedAt) {
	}

	/** Who the applicant is (never includes anything secret). */
	public record DriverInfo(Long id, String fullName, String email, String phoneNumber, boolean phoneVerified,
			Instant registeredAt) {
	}

	public record ApplicationDetail(ApplicationDto application, DriverInfo driver) {
	}

	/** Headline numbers for the admin dashboard. */
	public record Summary(long pendingReview, long approved, long rejected, long totalSubmitted) {
	}

	public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
	}
}
