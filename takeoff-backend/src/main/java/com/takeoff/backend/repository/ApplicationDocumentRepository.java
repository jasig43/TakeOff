package com.takeoff.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.takeoff.backend.model.ApplicationDocument;
import com.takeoff.backend.model.DocumentType;

public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, Long> {

	List<ApplicationDocument> findByApplicationIdOrderByDocType(Long applicationId);

	Optional<ApplicationDocument> findByApplicationIdAndDocType(Long applicationId, DocumentType docType);
}
