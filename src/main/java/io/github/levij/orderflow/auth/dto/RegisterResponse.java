package io.github.levij.orderflow.auth.dto;

import java.time.Instant;

import io.github.levij.orderflow.user.Role;
import io.github.levij.orderflow.user.User;

public record RegisterResponse(
		Long id,
		String email,
		Role role,
		Instant createdAt) {

	public static RegisterResponse from(User user) {
		return new RegisterResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
	}
}
