package com.takeoff.backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.takeoff.backend.model.ApplicationDocument;
import com.takeoff.backend.model.ApplicationStatus;
import com.takeoff.backend.model.DocumentType;
import com.takeoff.backend.model.DriverApplication;
import com.takeoff.backend.model.VehicleType;

/** The complete view of an application, used by both the driver and the administrator. */
public record ApplicationDto(Long id, String referenceId, ApplicationStatus status, boolean editable, Personal personal,
		Identity identity, Vehicle vehicle, List<DocumentDto> documents, Progress progress, Instant submittedAt,
		Instant decidedAt, String decisionNote) {

	public record Personal(LocalDate dateOfBirth, String addressLine, String city, String emergencyContactName,
			String emergencyContactPhone) {
	}

	public record Identity(String nationalId, String licenceNumber, String licenceClass, LocalDate licenceExpiry) {
	}

	public record Vehicle(VehicleType vehicleType, String plateNumber, String make, String model) {
	}

	/** Which parts are complete; {@code readyToSubmit} is true only when all four are. */
	public record Progress(boolean personal, boolean identity, boolean vehicle, boolean documents,
			boolean readyToSubmit) {
	}

	public record DocumentDto(DocumentType type, String filename, String contentType, long sizeBytes,
			Instant uploadedAt) {

		static DocumentDto from(ApplicationDocument document) {
			return new DocumentDto(document.getDocType(), document.getOriginalFilename(), document.getContentType(),
					document.getSizeBytes(), document.getUploadedAt());
		}
	}

	public static ApplicationDto from(DriverApplication app, List<ApplicationDocument> documents) {
		boolean documentsComplete = documents.stream().map(ApplicationDocument::getDocType).distinct().count() == DocumentType
			.values().length;
		Progress progress = new Progress(app.personalComplete(), app.identityComplete(), app.vehicleComplete(),
				documentsComplete, app.personalComplete() && app.identityComplete() && app.vehicleComplete()
						&& documentsComplete);
		return new ApplicationDto(app.getId(), app.getReferenceId(), app.getStatus(), app.editable(),
				new Personal(app.getDateOfBirth(), app.getAddressLine(), app.getCity(), app.getEmergencyContactName(),
						app.getEmergencyContactPhone()),
				new Identity(app.getNationalId(), app.getLicenceNumber(), app.getLicenceClass(),
						app.getLicenceExpiry()),
				new Vehicle(app.getVehicleType(), app.getPlateNumber(), app.getVehicleMake(), app.getVehicleModel()),
				documents.stream().map(DocumentDto::from).toList(), progress, app.getSubmittedAt(), app.getDecidedAt(),
				app.getDecisionNote());
	}
}
