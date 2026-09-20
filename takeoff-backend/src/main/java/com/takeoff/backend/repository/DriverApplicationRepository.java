package com.takeoff.backend.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.takeoff.backend.model.ApplicationStatus;
import com.takeoff.backend.model.DriverApplication;

public interface DriverApplicationRepository extends JpaRepository<DriverApplication, Long> {

	Optional<DriverApplication> findByUserId(Long userId);

	boolean existsByReferenceId(String referenceId);

	boolean existsByNationalIdAndUserIdNot(String nationalId, Long userId);

	boolean existsByPlateNumberAndUserIdNot(String plateNumber, Long userId);

	long countByStatus(ApplicationStatus status);

	/**
	 * The admin queue. {@code pattern} is a lower-cased SQL LIKE pattern ("%" matches everything); it is matched against
	 * the driver's name and email, the reference id and the plate number.
	 */
	@Query(value = """
			select a from DriverApplication a join User u on u.id = a.userId
			where a.status in :statuses
			  and (lower(u.fullName) like :pattern or lower(u.email) like :pattern
			       or lower(a.referenceId) like :pattern or lower(a.plateNumber) like :pattern)
			""", countQuery = """
			select count(a) from DriverApplication a join User u on u.id = a.userId
			where a.status in :statuses
			  and (lower(u.fullName) like :pattern or lower(u.email) like :pattern
			       or lower(a.referenceId) like :pattern or lower(a.plateNumber) like :pattern)
			""")
	Page<DriverApplication> search(@Param("statuses") Collection<ApplicationStatus> statuses,
			@Param("pattern") String pattern, Pageable pageable);
}
