package io.github.levij.orderflow.auth;

import java.util.List;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import io.github.levij.orderflow.user.UserService;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

	private final UserService userService;

	public DatabaseUserDetailsService(UserService userService) {
		this.userService = userService;
	}

	@Override
	public UserDetails loadUserByUsername(String email) {
		return userService.findByEmail(email)
				.map(user -> org.springframework.security.core.userdetails.User
						.withUsername(user.getEmail())
						.password(user.getPasswordHash())
						.authorities(List.of())
						.build())
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));
	}
}
