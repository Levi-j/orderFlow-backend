package io.github.levij.orderflow.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties("orderflow.jwt")
public record JwtProperties(

		@NotBlank(message = "must be set, for example through the ORDERFLOW_JWT_SECRET environment variable")
		String secret,

		@NotBlank
		String issuer,

		@NotNull
		Duration accessTokenTtl) {

	static final int MIN_SECRET_BYTES = 32;

	@AssertTrue(message = "orderflow.jwt.secret must be at least 32 bytes when UTF-8 encoded")
	public boolean isSecretLongEnough() {
		return secret == null || secret.isBlank()
				|| secret.getBytes(StandardCharsets.UTF_8).length >= MIN_SECRET_BYTES;
	}

	@Override
	public String toString() {
		return "JwtProperties[secret=***, issuer=" + issuer + ", accessTokenTtl=" + accessTokenTtl + "]";
	}
}
