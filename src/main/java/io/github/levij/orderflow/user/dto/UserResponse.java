package io.github.levij.orderflow.user.dto;

import java.time.Instant;

import io.github.levij.orderflow.user.Role;
import io.github.levij.orderflow.user.User;

public record UserResponse(
		Long id,
		String email,
		Role role,
		Instant createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
	}
}
