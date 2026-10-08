package io.github.levij.orderflow.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

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
			throw loginFailed();
		}

		try {
			authenticationManager.authenticate(
					UsernamePasswordAuthenticationToken.unauthenticated(normalizedEmail, rawPassword));
		}
		catch (BadCredentialsException ex) {
			throw loginFailed();
		}

		User user = userService.findByEmail(normalizedEmail).orElseThrow(AuthService::loginFailed);
		return jwtTokenService.issue(user.getId(), user.getRole());
	}

	private static InvalidCredentialsException loginFailed() {
		log.atWarn()
				.addKeyValue("eventName", "auth.login_failed")
				.log("Login failed");
		return new InvalidCredentialsException();
	}
}
