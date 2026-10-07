package io.github.levij.orderflow.auth;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import io.github.levij.orderflow.user.Role;

public class RoleClaimConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

	@Override
	public Collection<GrantedAuthority> convert(Jwt jwt) {
		String role = jwt.getClaimAsString(JwtTokenService.ROLE_CLAIM);
		return Arrays.stream(Role.values())
				.filter(knownRole -> knownRole.name().equals(role))
				.<GrantedAuthority>map(knownRole -> new SimpleGrantedAuthority("ROLE_" + knownRole.name()))
				.findFirst()
				.map(List::of)
				.orElse(List.of());
	}
}
