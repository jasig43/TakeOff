package com.takeoff.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.takeoff.backend.model.OtpToken;

public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {

	/** The most recently issued token for a user, whatever its state. */
	Optional<OtpToken> findFirstByUserIdOrderByIdDesc(Long userId);

	/** Marks every still-active token of the user as consumed, so only the newest code can work. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update OtpToken t set t.consumed = true where t.userId = :userId and t.consumed = false")
	int invalidateActiveTokens(@Param("userId") Long userId);
}
