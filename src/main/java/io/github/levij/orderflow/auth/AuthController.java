package io.github.levij.orderflow.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.levij.orderflow.auth.dto.RegisterRequest;
import io.github.levij.orderflow.auth.dto.RegisterResponse;
import io.github.levij.orderflow.user.User;
import io.github.levij.orderflow.user.UserService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final UserService userService;

	public AuthController(UserService userService) {
		this.userService = userService;
	}

	@PostMapping("/register")
	public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
		User user = userService.registerCustomer(request.email(), request.password());
		return ResponseEntity.status(HttpStatus.CREATED).body(RegisterResponse.from(user));
	}
}
