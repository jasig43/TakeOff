package com.takeoff.backend.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.takeoff.backend.dto.ApplicationDto;
import com.takeoff.backend.dto.IdentityRequest;
import com.takeoff.backend.dto.PersonalDetailsRequest;
import com.takeoff.backend.dto.VehicleRequest;
import com.takeoff.backend.exception.ApiException;
import com.takeoff.backend.exception.FieldValidationException;
import com.takeoff.backend.model.ApplicationDocument;
import com.takeoff.backend.model.ApplicationStatus;
import com.takeoff.backend.model.DocumentType;
import com.takeoff.backend.model.DriverApplication;
import com.takeoff.backend.repository.ApplicationDocumentRepository;
import com.takeoff.backend.repository.DriverApplicationRepository;
import com.takeoff.backend.service.DocumentStorageService.StoredFile;

/**
 * The driver's side of an application: fill in the sections, upload documents, submit, and track.
 *
 * <p>Every method takes the user id of the authenticated driver and only ever touches that driver's own application, so
 * one driver cannot read or change another's. Editing is allowed while the application is a DRAFT or REJECTED (editing a
 * rejected application reopens it as a draft); once it is PENDING_REVIEW or APPROVED it is locked.
 */
@Service
public class ApplicationService {

	private static final int MINIMUM_AGE = 18;
	private static final String REFERENCE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I

	private final DriverApplicationRepository applications;
	private final ApplicationDocumentRepository documents;
	private final DocumentStorageService storage;
	private final NotificationService notifications;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public ApplicationService(DriverApplicationRepository applications, ApplicationDocumentRepository documents,
			DocumentStorageService storage, NotificationService notifications, Clock clock) {
		this.applications = applications;
		this.documents = documents;
		this.storage = storage;
		this.notifications = notifications;
		this.clock = clock;
	}

	@Transactional
	public ApplicationDto get(Long userId) {
		return toDto(getOrCreate(userId));
	}

	@Transactional
	public ApplicationDto savePersonal(Long userId, PersonalDetailsRequest request) {
		int age = Period.between(request.dateOfBirth(), LocalDate.now(clock)).getYears();
		if (age < MINIMUM_AGE) {
			throw new FieldValidationException("dateOfBirth", "You must be at least " + MINIMUM_AGE + " years old to apply.");
		}
		if (age > 100) {
			throw new FieldValidationException("dateOfBirth", "Enter a valid date of birth.");
		}
		DriverApplication app = editable(userId);
		app.setPersonal(request.dateOfBirth(), request.addressLine().trim(), request.city().trim(),
				request.emergencyContactName().trim(), request.emergencyContactPhone().trim());
		return toDto(applications.save(app));
	}

	@Transactional
	public ApplicationDto saveIdentity(Long userId, IdentityRequest request) {
		String nationalId = request.nationalId().trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
		if (applications.existsByNationalIdAndUserIdNot(nationalId, userId)) {
			throw new ApiException(HttpStatus.CONFLICT, "NATIONAL_ID_IN_USE",
					"An application with this national ID already exists. Contact support if this is a mistake.");
		}
		DriverApplication app = editable(userId);
		app.setIdentity(nationalId, request.licenceNumber().trim().toUpperCase(Locale.ROOT),
				request.licenceClass().trim(), request.licenceExpiry());
		return toDto(applications.save(app));
	}

	@Transactional
	public ApplicationDto saveVehicle(Long userId, VehicleRequest request) {
		String plate = request.plateNumber().trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
		if (applications.existsByPlateNumberAndUserIdNot(plate, userId)) {
			throw new ApiException(HttpStatus.CONFLICT, "PLATE_IN_USE",
					"An application with this registration number already exists. Contact support if this is a mistake.");
		}
		DriverApplication app = editable(userId);
		app.setVehicle(request.vehicleType(), plate, request.make().trim(), request.model().trim());
		return toDto(applications.save(app));
	}

	@Transactional
	public ApplicationDto uploadDocument(Long userId, DocumentType type, MultipartFile file) {
		DriverApplication app = editable(userId);
		StoredFile stored = storage.store(file);
		try {
			ApplicationDocument existing = documents.findByApplicationIdAndDocType(app.getId(), type).orElse(null);
			if (existing == null) {
				documents.save(new ApplicationDocument(app.getId(), type, stored.displayName(), stored.contentType(),
						stored.sizeBytes(), stored.storageKey(), clock.instant()));
			}
			else {
				String oldKey = existing.getStorageKey();
				existing.replaceFile(stored.displayName(), stored.contentType(), stored.sizeBytes(), stored.storageKey(),
						clock.instant());
				documents.save(existing);
				deleteFileAfterCommit(oldKey);
			}
			documents.flush();
		}
		catch (RuntimeException ex) {
			storage.delete(stored.storageKey()); // do not leave an orphan file behind
			throw ex;
		}
		return toDto(app);
	}

	@Transactional
	public ApplicationDto deleteDocument(Long userId, DocumentType type) {
		DriverApplication app = editable(userId);
		documents.findByApplicationIdAndDocType(app.getId(), type).ifPresent(document -> {
			documents.delete(document);
			deleteFileAfterCommit(document.getStorageKey());
		});
		return toDto(app);
	}

	@Transactional(readOnly = true)
	public DocumentDownload openDocument(Long userId, DocumentType type) {
		DriverApplication app = applications.findByUserId(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found."));
		ApplicationDocument document = documents.findByApplicationIdAndDocType(app.getId(), type)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found."));
		return new DocumentDownload(storage.load(document.getStorageKey()), document.getContentType(),
				document.getOriginalFilename());
	}

	@Transactional
	public ApplicationDto submit(Long userId) {
		DriverApplication app = getOrCreate(userId);
		if (app.getStatus() == ApplicationStatus.REJECTED) {
			throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_UNCHANGED",
					"Update your application to address the reviewer's note before submitting again.");
		}
		if (app.getStatus() != ApplicationStatus.DRAFT) {
			throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_LOCKED", "This application has already been submitted.");
		}

		List<DocumentType> uploaded = documents.findByApplicationIdOrderByDocType(app.getId())
			.stream()
			.map(ApplicationDocument::getDocType)
			.toList();
		List<String> missing = new ArrayList<>();
		if (!app.personalComplete()) {
			missing.add("personal details");
		}
		if (!app.identityComplete()) {
			missing.add("identity and licence");
		}
		if (!app.vehicleComplete()) {
			missing.add("vehicle details");
		}
		for (DocumentType type : DocumentType.values()) {
			if (!uploaded.contains(type)) {
				missing.add(type.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " document");
			}
		}
		if (!missing.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "APPLICATION_INCOMPLETE",
					"Complete these before submitting: " + String.join(", ", missing) + ".");
		}

		if (app.getReferenceId() == null) {
			app.setReferenceId(newReferenceId());
		}
		app.clearDecision();
		app.setStatus(ApplicationStatus.PENDING_REVIEW);
		app.setSubmittedAt(clock.instant());
		applications.save(app);

		notifications.create(userId, "APPLICATION_SUBMITTED", "Application submitted",
				"Your application " + app.getReferenceId() + " was submitted and is waiting for review.");
		return toDto(app);
	}

	// ------------------------------------------------------------------ helpers

	private DriverApplication getOrCreate(Long userId) {
		return applications.findByUserId(userId).orElseGet(() -> applications.saveAndFlush(new DriverApplication(userId)));
	}

	/** Returns the driver's application ready to be edited, or fails if it is locked. Editing a rejected one reopens it. */
	private DriverApplication editable(Long userId) {
		DriverApplication app = getOrCreate(userId);
		if (!app.editable()) {
			throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_LOCKED",
					"This application has been submitted and can no longer be edited.");
		}
		if (app.getStatus() == ApplicationStatus.REJECTED) {
			app.setStatus(ApplicationStatus.DRAFT);
		}
		return app;
	}

	private ApplicationDto toDto(DriverApplication app) {
		return ApplicationDto.from(app, documents.findByApplicationIdOrderByDocType(app.getId()));
	}

	private String newReferenceId() {
		String date = LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE);
		for (int attempt = 0; attempt < 10; attempt++) {
			StringBuilder code = new StringBuilder(6);
			for (int i = 0; i < 6; i++) {
				code.append(REFERENCE_ALPHABET.charAt(random.nextInt(REFERENCE_ALPHABET.length())));
			}
			String candidate = "TKO-" + date + "-" + code;
			if (!applications.existsByReferenceId(candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException("Could not generate a unique reference id");
	}

	/** Old files are removed only once the database change that replaced them has committed. */
	private void deleteFileAfterCommit(String storageKey) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					storage.delete(storageKey);
				}
			});
		}
		else {
			storage.delete(storageKey);
		}
	}
}
