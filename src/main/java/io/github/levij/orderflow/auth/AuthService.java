package io.github.levij.orderflow.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import io.github.levij.orderflow.common.error.InvalidCredentialsException;
import io.github.levij.orderflow.user.User;
import io.github.levij.orderflow.user.UserService;

@Service
public class AuthService {

	private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

	private final AuthenticationManager authenticationManager;
	private final UserService userService;
	private final JwtTokenService jwtTokenService;

	public AuthService(AuthenticationManager authenticationManager, UserService userService,
			JwtTokenService jwtTokenService) {
		this.authenticationManager = authenticationManager;
		this.userService = userService;
		this.jwtTokenService = jwtTokenService;
	}

	public AccessToken login(String email, String rawPassword) {
		String normalizedEmail = email.toLowerCase(Locale.ROOT);

		if (rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
			throw new InvalidCredentialsException();
		}

		try {
			authenticationManager.authenticate(
					UsernamePasswordAuthenticationToken.unauthenticated(normalizedEmail, rawPassword));
		}
		catch (BadCredentialsException ex) {
			throw new InvalidCredentialsException();
		}

		User user = userService.findByEmail(normalizedEmail).orElseThrow(InvalidCredentialsException::new);
		return jwtTokenService.issue(user.getId(), user.getRole());
	}
}
