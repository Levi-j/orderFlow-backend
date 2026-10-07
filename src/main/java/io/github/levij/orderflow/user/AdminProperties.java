package io.github.levij.orderflow.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@ConfigurationProperties("orderflow.admin")
public record AdminProperties(

		@NotBlank
		@Email
		@Size(max = 254)
		String email,

		@NotNull
		@PasswordPolicy
		String password) {

	boolean hasEmail() {
		return email != null && !email.isBlank();
	}

	boolean hasPassword() {
		return password != null && !password.isEmpty();
	}

	@Override
	public String toString() {
		return "AdminProperties[email=***, password=***]";
	}
}
