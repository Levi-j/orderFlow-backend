package io.github.levij.orderflow.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.levij.orderflow.auth.dto.LoginRequest;
import io.github.levij.orderflow.auth.dto.LoginResponse;
import io.github.levij.orderflow.auth.dto.RegisterRequest;
import io.github.levij.orderflow.auth.dto.RegisterResponse;
import io.github.levij.orderflow.user.User;
import io.github.levij.orderflow.user.UserService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final UserService userService;
	private final AuthService authService;

	public AuthController(UserService userService, AuthService authService) {
		this.userService = userService;
		this.authService = authService;
	}

	@PostMapping("/register")
	public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
		User user = userService.registerCustomer(request.email(), request.password());
		return ResponseEntity.status(HttpStatus.CREATED).body(RegisterResponse.from(user));
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		AccessToken token = authService.login(request.email(), request.password());
		return new LoginResponse(token.value(), "Bearer", token.expiresInSeconds());
	}
}
