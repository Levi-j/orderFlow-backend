package io.github.levij.orderflow.auth;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import io.github.levij.orderflow.user.Role;

@Service
public class JwtTokenService {

	static final String ROLE_CLAIM = "role";

	private final JwtEncoder jwtEncoder;
	private final String issuer;
	private final Duration accessTokenTtl;

	public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties properties) {
		this.jwtEncoder = jwtEncoder;
		this.issuer = properties.issuer();
		this.accessTokenTtl = properties.accessTokenTtl();
	}

	public AccessToken issue(Long userId, Role role) {
		Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(issuer)
				.subject(String.valueOf(userId))
				.issuedAt(issuedAt)
				.expiresAt(issuedAt.plus(accessTokenTtl))
				.claim(ROLE_CLAIM, role.name())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

		String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new AccessToken(tokenValue, accessTokenTtl.toSeconds());
	}
}
