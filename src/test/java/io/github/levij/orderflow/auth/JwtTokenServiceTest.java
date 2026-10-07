package io.github.levij.orderflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import io.github.levij.orderflow.user.Role;

class JwtTokenServiceTest {

	private final JwtConfig jwtConfig = new JwtConfig();

	private final JwtProperties properties = properties(randomSecret());

	private final JwtTokenService tokenService = new JwtTokenService(jwtConfig.jwtEncoder(properties), properties);

	private final JwtDecoder decoder = jwtConfig.jwtDecoder(properties);

	@Test
	void issuedTokenIsValidAndCarriesTheExpectedClaims() {
		AccessToken token = tokenService.issue(42L, Role.CUSTOMER);

		Jwt jwt = decoder.decode(token.value());

		assertThat(jwt.getSubject()).isEqualTo("42");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("orderflow");
		assertThat(jwt.getClaimAsString("role")).isEqualTo("CUSTOMER");
		assertThat(jwt.getIssuedAt()).isNotNull();
		assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plus(Duration.ofMinutes(30)));
		assertThat(token.expiresInSeconds()).isEqualTo(1800);
		assertThat(jwt.getClaims()).doesNotContainKeys("email", "password", "passwordHash");
	}

	@Test
	void tokenSignedWithAnotherSecretIsRejected() {
		JwtProperties otherProperties = properties(randomSecret());
		JwtTokenService otherService = new JwtTokenService(jwtConfig.jwtEncoder(otherProperties), otherProperties);
		String foreignToken = otherService.issue(42L, Role.CUSTOMER).value();

		assertThatThrownBy(() -> decoder.decode(foreignToken)).isInstanceOf(JwtException.class);
	}

	private static JwtProperties properties(String secret) {
		return new JwtProperties(secret, "orderflow", Duration.ofMinutes(30));
	}

	private static String randomSecret() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
