package com.takeoff.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.takeoff.backend.model.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	List<Notification> findTop50ByUserIdOrderByIdDesc(Long userId);

	long countByUserIdAndReadFalse(Long userId);

	Optional<Notification> findByIdAndUserId(Long id, Long userId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Notification n set n.read = true where n.userId = :userId and n.read = false")
	int markAllRead(@Param("userId") Long userId);
}
