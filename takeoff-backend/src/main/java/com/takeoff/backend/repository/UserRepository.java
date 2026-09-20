package com.takeoff.backend.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.takeoff.backend.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	Optional<User> findByPhoneNumber(String phoneNumber);

	boolean existsByEmail(String email);

	boolean existsByPhoneNumber(String phoneNumber);

	/** Accounts whose name, email or phone contains the (already lower-cased and LIKE-escaped) pattern. */
	@Query(value = """
			select u from User u
			where lower(u.fullName) like :pattern or lower(u.email) like :pattern or u.phoneNumber like :pattern
			""", countQuery = """
			select count(u) from User u
			where lower(u.fullName) like :pattern or lower(u.email) like :pattern or u.phoneNumber like :pattern
			""")
	Page<User> search(@Param("pattern") String pattern, Pageable pageable);
}
