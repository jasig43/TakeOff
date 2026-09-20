package com.takeoff.backend.service;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import com.takeoff.backend.dto.AdminDtos.ApplicationDetail;
import com.takeoff.backend.dto.AdminDtos.ApplicationSummary;
import com.takeoff.backend.dto.AdminDtos.DriverInfo;
import com.takeoff.backend.dto.AdminDtos.PageResponse;
import com.takeoff.backend.dto.AdminDtos.Summary;
import com.takeoff.backend.dto.ApplicationDto;
import com.takeoff.backend.dto.DecisionEvent;
import com.takeoff.backend.dto.DecisionRequest;
import com.takeoff.backend.exception.ApiException;
import com.takeoff.backend.exception.FieldValidationException;
import com.takeoff.backend.model.ApplicationDocument;
import com.takeoff.backend.model.ApplicationStatus;
import com.takeoff.backend.model.DocumentType;
import com.takeoff.backend.model.DriverApplication;
import com.takeoff.backend.model.User;
import com.takeoff.backend.repository.ApplicationDocumentRepository;
import com.takeoff.backend.repository.DriverApplicationRepository;
import com.takeoff.backend.repository.UserRepository;

/**
 * The administrator's side: see the queue, inspect an application and its documents, and approve or reject it.
 * Drafts are private to the driver and are invisible here. Role enforcement is done by the security chain
 * ({@code /api/v1/admin/**} requires ROLE_LOGISTICS_ADMIN); this class assumes its caller is an administrator.
 */
@Service
public class AdminApplicationService {

	private static final Set<ApplicationStatus> SUBMITTED = EnumSet.of(ApplicationStatus.PENDING_REVIEW,
			ApplicationStatus.APPROVED, ApplicationStatus.REJECTED);
	private static final int MAX_PAGE_SIZE = 50;

	private final DriverApplicationRepository applications;
	private final ApplicationDocumentRepository documents;
	private final UserRepository users;
	private final DocumentStorageService storage;
	private final NotificationService notifications;
	private final NotificationProducerService producer;
	private final TransactionOperations transactions;
	private final Clock clock;

	public AdminApplicationService(DriverApplicationRepository applications, ApplicationDocumentRepository documents,
			UserRepository users, DocumentStorageService storage, NotificationService notifications,
			NotificationProducerService producer, TransactionOperations transactions, Clock clock) {
		this.applications = applications;
		this.documents = documents;
		this.users = users;
		this.storage = storage;
		this.notifications = notifications;
		this.producer = producer;
		this.transactions = transactions;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Summary summary() {
		long pending = applications.countByStatus(ApplicationStatus.PENDING_REVIEW);
		long approved = applications.countByStatus(ApplicationStatus.APPROVED);
		long rejected = applications.countByStatus(ApplicationStatus.REJECTED);
		return new Summary(pending, approved, rejected, pending + approved + rejected);
	}

	@Transactional(readOnly = true)
	public PageResponse<ApplicationSummary> search(ApplicationStatus status, String query, int page, int size) {
		if (status == ApplicationStatus.DRAFT) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS_FILTER",
					"Drafts are private to the driver. Filter by PENDING_REVIEW, APPROVED or REJECTED.");
		}
		Set<ApplicationStatus> statuses = status == null ? SUBMITTED : EnumSet.of(status);
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		int safePage = Math.max(page, 0);
		String pattern = "%" + (query == null ? "" : escapeLike(query.trim().toLowerCase(Locale.ROOT))) + "%";

		Page<DriverApplication> result = applications.search(statuses, pattern,
				PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("submittedAt"), Sort.Order.desc("id"))));

		Map<Long, User> drivers = users.findAllById(result.getContent().stream().map(DriverApplication::getUserId).toList())
			.stream()
			.collect(Collectors.toMap(User::getId, Function.identity()));
		List<ApplicationSummary> items = result.getContent().stream().map(app -> {
			User driver = drivers.get(app.getUserId());
			return new ApplicationSummary(app.getId(), app.getReferenceId(), app.getStatus(),
					driver == null ? "(deleted user)" : driver.getFullName(), driver == null ? "" : driver.getEmail(),
					driver == null ? "" : driver.getPhoneNumber(), app.getPlateNumber(), app.getSubmittedAt(),
					app.getDecidedAt());
		}).toList();
		return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(),
				result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public ApplicationDetail detail(Long applicationId) {
		return toDetail(submittedApplication(applicationId));
	}

	@Transactional(readOnly = true)
	public DocumentDownload openDocument(Long applicationId, DocumentType type) {
		DriverApplication app = submittedApplication(applicationId);
		ApplicationDocument document = documents.findByApplicationIdAndDocType(app.getId(), type)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found."));
		return new DocumentDownload(storage.load(document.getStorageKey()), document.getContentType(),
				document.getOriginalFilename());
	}

	/**
	 * PENDING_REVIEW -> APPROVED | REJECTED. The status change and the driver's in-app notification are committed
	 * together; only afterwards is the RabbitMQ event published (so the listener can never run before the commit).
	 */
	public ApplicationDetail decide(Long adminId, Long applicationId, DecisionRequest request) {
		ApplicationStatus decision = request.status();
		if (decision != ApplicationStatus.APPROVED && decision != ApplicationStatus.REJECTED) {
			throw new FieldValidationException("status", "The decision must be APPROVED or REJECTED.");
		}
		String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
		if (decision == ApplicationStatus.REJECTED && note == null) {
			throw new FieldValidationException("note", "Give the applicant a reason for the rejection.");
		}

		DriverApplication decided = transactions.execute(status -> {
			DriverApplication app = submittedApplication(applicationId);
			if (app.getStatus() != ApplicationStatus.PENDING_REVIEW) {
				throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION",
						"Only applications that are pending review can be approved or rejected.");
			}
			app.decide(decision, adminId, note, clock.instant());
			applications.saveAndFlush(app);

			boolean approved = decision == ApplicationStatus.APPROVED;
			notifications.create(app.getUserId(), approved ? "APPLICATION_APPROVED" : "APPLICATION_REJECTED",
					approved ? "Application approved" : "Application not approved",
					approved ? "Congratulations! Your driver application " + app.getReferenceId() + " has been approved."
							: "Your driver application " + app.getReferenceId() + " was not approved. Reason: " + note
									+ " You can update your application and submit it again.");
			return app;
		});

		producer.publishDecision(DecisionEvent.of(decided.getId(), decided.getUserId(), decision.name(),
				decided.getReferenceId(), clock.instant()));
		return detail(applicationId);
	}

	// ------------------------------------------------------------------ helpers

	/** Loads an application an admin may see: it must exist and not be a private draft. */
	private DriverApplication submittedApplication(Long applicationId) {
		return applications.findById(applicationId)
			.filter(app -> app.getStatus().isSubmitted())
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Application not found."));
	}

	private ApplicationDetail toDetail(DriverApplication app) {
		User driver = users.findById(app.getUserId()).orElse(null);
		DriverInfo info = driver == null ? new DriverInfo(app.getUserId(), "(deleted user)", "", "", false, null)
				: new DriverInfo(driver.getId(), driver.getFullName(), driver.getEmail(), driver.getPhoneNumber(),
						driver.isPhoneVerified(), driver.getCreatedAt());
		return new ApplicationDetail(
				ApplicationDto.from(app, documents.findByApplicationIdOrderByDocType(app.getId())), info);
	}

	/** Search text is data, not a pattern: escape LIKE wildcards. */
	private static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
