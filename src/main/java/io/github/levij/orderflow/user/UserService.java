package io.github.levij.orderflow.user;

import java.util.Locale;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.levij.orderflow.common.error.ConflictException;
import io.github.levij.orderflow.common.error.ErrorCode;

@Service
public class UserService {

	private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_users_email";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public User registerCustomer(String email, String rawPassword) {
		String normalizedEmail = email.toLowerCase(Locale.ROOT);

		if (userRepository.existsByEmail(normalizedEmail)) {
			throw emailAlreadyRegistered();
		}

		String passwordHash = passwordEncoder.encode(rawPassword);
		User user = User.registerCustomer(normalizedEmail, passwordHash);
		try {
			return userRepository.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			if (violatesEmailUniqueConstraint(ex)) {
				throw emailAlreadyRegistered();
			}
			throw ex;
		}
	}

	private static ConflictException emailAlreadyRegistered() {
		return new ConflictException(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with that email already exists.");
	}

	private static boolean violatesEmailUniqueConstraint(DataIntegrityViolationException ex) {
		for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation
					&& EMAIL_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName())) {
				return true;
			}
		}
		return false;
	}
}
