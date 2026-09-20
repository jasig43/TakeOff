package com.takeoff.backend.model;

import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One application per driver. Sections are filled in independently while it is a DRAFT; the whole thing is locked
 * once submitted. Enums are forced to VARCHAR for the same reason as {@link User#getRole()}.
 */
@Entity
@Table(name = "driver_applications")
public class DriverApplication {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Version
	private Long version;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "reference_id", length = 30)
	private String referenceId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private ApplicationStatus status = ApplicationStatus.DRAFT;

	// personal details
	@Column(name = "date_of_birth")
	private LocalDate dateOfBirth;
	@Column(name = "address_line", length = 200)
	private String addressLine;
	@Column(length = 100)
	private String city;
	@Column(name = "emergency_contact_name", length = 100)
	private String emergencyContactName;
	@Column(name = "emergency_contact_phone", length = 20)
	private String emergencyContactPhone;

	// identity and licence
	@Column(name = "national_id", length = 20)
	private String nationalId;
	@Column(name = "licence_number", length = 30)
	private String licenceNumber;
	@Column(name = "licence_class", length = 20)
	private String licenceClass;
	@Column(name = "licence_expiry")
	private LocalDate licenceExpiry;

	// vehicle
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "vehicle_type", length = 20)
	private VehicleType vehicleType;
	@Column(name = "plate_number", length = 20)
	private String plateNumber;
	@Column(name = "vehicle_make", length = 50)
	private String vehicleMake;
	@Column(name = "vehicle_model", length = 50)
	private String vehicleModel;

	// review
	@Column(name = "submitted_at")
	private Instant submittedAt;
	@Column(name = "decided_at")
	private Instant decidedAt;
	@Column(name = "decided_by")
	private Long decidedBy;
	@Column(name = "decision_note", length = 500)
	private String decisionNote;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected DriverApplication() {
		// for JPA
	}

	public DriverApplication(Long userId) {
		this.userId = userId;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	// ---- section completeness (what "ready to submit" is built from)

	public boolean personalComplete() {
		return dateOfBirth != null && filled(addressLine) && filled(city) && filled(emergencyContactName)
				&& filled(emergencyContactPhone);
	}

	public boolean identityComplete() {
		return filled(nationalId) && filled(licenceNumber) && filled(licenceClass) && licenceExpiry != null;
	}

	public boolean vehicleComplete() {
		return vehicleType != null && filled(plateNumber) && filled(vehicleMake) && filled(vehicleModel);
	}

	/** DRAFT and REJECTED applications can be edited (editing a rejected one reopens it as a draft). */
	public boolean editable() {
		return status == ApplicationStatus.DRAFT || status == ApplicationStatus.REJECTED;
	}

	private static boolean filled(String value) {
		return value != null && !value.isBlank();
	}

	// ---- accessors

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getReferenceId() {
		return referenceId;
	}

	public void setReferenceId(String referenceId) {
		this.referenceId = referenceId;
	}

	public ApplicationStatus getStatus() {
		return status;
	}

	public void setStatus(ApplicationStatus status) {
		this.status = status;
	}

	public LocalDate getDateOfBirth() {
		return dateOfBirth;
	}

	public String getAddressLine() {
		return addressLine;
	}

	public String getCity() {
		return city;
	}

	public String getEmergencyContactName() {
		return emergencyContactName;
	}

	public String getEmergencyContactPhone() {
		return emergencyContactPhone;
	}

	public void setPersonal(LocalDate dateOfBirth, String addressLine, String city, String emergencyContactName,
			String emergencyContactPhone) {
		this.dateOfBirth = dateOfBirth;
		this.addressLine = addressLine;
		this.city = city;
		this.emergencyContactName = emergencyContactName;
		this.emergencyContactPhone = emergencyContactPhone;
	}

	public String getNationalId() {
		return nationalId;
	}

	public String getLicenceNumber() {
		return licenceNumber;
	}

	public String getLicenceClass() {
		return licenceClass;
	}

	public LocalDate getLicenceExpiry() {
		return licenceExpiry;
	}

	public void setIdentity(String nationalId, String licenceNumber, String licenceClass, LocalDate licenceExpiry) {
		this.nationalId = nationalId;
		this.licenceNumber = licenceNumber;
		this.licenceClass = licenceClass;
		this.licenceExpiry = licenceExpiry;
	}

	public VehicleType getVehicleType() {
		return vehicleType;
	}

	public String getPlateNumber() {
		return plateNumber;
	}

	public String getVehicleMake() {
		return vehicleMake;
	}

	public String getVehicleModel() {
		return vehicleModel;
	}

	public void setVehicle(VehicleType vehicleType, String plateNumber, String vehicleMake, String vehicleModel) {
		this.vehicleType = vehicleType;
		this.plateNumber = plateNumber;
		this.vehicleMake = vehicleMake;
		this.vehicleModel = vehicleModel;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

	public void setSubmittedAt(Instant submittedAt) {
		this.submittedAt = submittedAt;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

	public Long getDecidedBy() {
		return decidedBy;
	}

	public String getDecisionNote() {
		return decisionNote;
	}

	public void decide(ApplicationStatus status, Long adminId, String note, Instant at) {
		this.status = status;
		this.decidedBy = adminId;
		this.decisionNote = note;
		this.decidedAt = at;
	}

	/** Clears the previous decision when a rejected application is resubmitted. */
	public void clearDecision() {
		this.decidedBy = null;
		this.decisionNote = null;
		this.decidedAt = null;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
