package com.takeoff.backend.model;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Metadata of an uploaded document. The bytes live on disk under {@link #getStorageKey()}. */
@Entity
@Table(name = "application_documents")
public class ApplicationDocument {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "application_id", nullable = false, updatable = false)
	private Long applicationId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "doc_type", nullable = false, length = 30, updatable = false)
	private DocumentType docType;

	@Column(name = "original_filename", nullable = false, length = 255)
	private String originalFilename;

	@Column(name = "content_type", nullable = false, length = 100)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(name = "storage_key", nullable = false, length = 80)
	private String storageKey;

	@Column(name = "uploaded_at", nullable = false)
	private Instant uploadedAt;

	protected ApplicationDocument() {
		// for JPA
	}

	public ApplicationDocument(Long applicationId, DocumentType docType, String originalFilename, String contentType,
			long sizeBytes, String storageKey, Instant uploadedAt) {
		this.applicationId = applicationId;
		this.docType = docType;
		this.originalFilename = originalFilename;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.storageKey = storageKey;
		this.uploadedAt = uploadedAt;
	}

	public Long getId() {
		return id;
	}

	public Long getApplicationId() {
		return applicationId;
	}

	public DocumentType getDocType() {
		return docType;
	}

	public String getOriginalFilename() {
		return originalFilename;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public String getStorageKey() {
		return storageKey;
	}

	public Instant getUploadedAt() {
		return uploadedAt;
	}

	/** Replaces the stored file (a re-upload of the same document type). */
	public void replaceFile(String originalFilename, String contentType, long sizeBytes, String storageKey,
			Instant uploadedAt) {
		this.originalFilename = originalFilename;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.storageKey = storageKey;
		this.uploadedAt = uploadedAt;
	}
}
