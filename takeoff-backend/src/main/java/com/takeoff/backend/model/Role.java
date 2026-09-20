package com.takeoff.backend.model;

/** Application roles. Public registration can only ever produce {@link #APPLICANT_DRIVER}. */
public enum Role {

	APPLICANT_DRIVER,
	LOGISTICS_ADMIN;

	/** Spring Security authority string, e.g. {@code ROLE_APPLICANT_DRIVER}. */
	public String authority() {
		return "ROLE_" + name();
	}
}
