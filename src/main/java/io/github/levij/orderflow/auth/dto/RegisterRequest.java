package io.github.levij.orderflow.auth.dto;

import io.github.levij.orderflow.user.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

		@NotBlank
		@Email
		@Size(max = 254)
		String email,

		@NotNull
		@PasswordPolicy
		String password) {

	@Override
	public String toString() {
		return "RegisterRequest[email=" + email + ", password=***]";
	}
}
