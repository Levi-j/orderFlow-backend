package io.github.levij.orderflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;

class RoleClaimConverterTest {

	private final RoleClaimConverter converter = new RoleClaimConverter();

	@Test
	void knownRolesBecomeSpringSecurityRoles() {
		assertThat(authorities(Map.of("role", "ADMIN"))).containsExactly("ROLE_ADMIN");
		assertThat(authorities(Map.of("role", "CUSTOMER"))).containsExactly("ROLE_CUSTOMER");
	}

	@Test
	void missingOrUnknownRoleGrantsNothing() {
		assertThat(authorities(Map.of())).isEmpty();
		assertThat(authorities(Map.of("role", "SUPERUSER"))).isEmpty();
		assertThat(authorities(Map.of("role", "admin"))).isEmpty();
		assertThat(authorities(Map.of("role", "ROLE_ADMIN"))).isEmpty();
		assertThat(authorities(Map.of("role", "ADMIN CUSTOMER"))).isEmpty();
	}

	private Collection<String> authorities(Map<String, Object> extraClaims) {
		Jwt.Builder jwt = Jwt.withTokenValue("token")
				.header("alg", "HS256")
				.subject("1")
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(60));
		extraClaims.forEach(jwt::claim);
		Collection<GrantedAuthority> granted = converter.convert(jwt.build());
		return AuthorityUtils.authorityListToSet(granted);
	}
}
