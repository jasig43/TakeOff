package com.takeoff.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.takeoff.backend.dto.DriverProfileDto;
import com.takeoff.backend.exception.ApiException;
import com.takeoff.backend.repository.UserRepository;

@Service
public class DriverService {

	private final UserRepository users;

	public DriverService(UserRepository users) {
		this.users = users;
	}

	@Transactional(readOnly = true)
	public DriverProfileDto getProfile(Long userId) {
		return users.findById(userId)
			.map(DriverProfileDto::from)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Profile not found."));
	}
}
