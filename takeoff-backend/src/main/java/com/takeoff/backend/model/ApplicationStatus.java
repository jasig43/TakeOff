package com.takeoff.backend.model;

/** Life cycle of a driver application: DRAFT -> PENDING_REVIEW -> APPROVED | REJECTED (a rejected one can be revised). */
public enum ApplicationStatus {

	DRAFT,
	PENDING_REVIEW,
	APPROVED,
	REJECTED;

	/** Statuses an administrator can see and filter by; drafts are private to the driver. */
	public boolean isSubmitted() {
		return this != DRAFT;
	}
}
