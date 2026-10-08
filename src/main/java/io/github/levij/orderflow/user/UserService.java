package io.github.levij.orderflow.user;

import java.util.Locale;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.levij.orderflow.common.error.ConflictException;
import io.github.levij.orderflow.common.error.ErrorCode;
import io.github.levij.orderflow.common.error.NotFoundException;

@Service
public class UserService {

	private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_users_email";
	private static final Logger log = LoggerFactory.getLogger(UserService.class);

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
		User user;
		try {
			user = userRepository.saveAndFlush(User.registerCustomer(normalizedEmail, passwordHash));
		}
		catch (DataIntegrityViolationException ex) {
			if (violatesEmailUniqueConstraint(ex)) {
				throw emailAlreadyRegistered();
			}
			throw ex;
		}

		log.atInfo()
				.addKeyValue("eventName", "user.registered")
				.addKeyValue("userId", user.getId())
				.addKeyValue("role", user.getRole())
				.log("User registered");
		return user;
	}

	@Transactional
	public boolean ensureBootstrapAdmin(String email, String rawPassword) {
		String normalizedEmail = email.toLowerCase(Locale.ROOT);

		Optional<User> existing = userRepository.findByEmail(normalizedEmail);
		if (existing.isPresent()) {
			if (existing.get().getRole() == Role.ADMIN) {
				return false;
			}
			throw new IllegalStateException(
					"The bootstrap admin email (ORDERFLOW_ADMIN_EMAIL) already belongs to a non-admin account. "
							+ "It will not be promoted automatically.");
		}

		String passwordHash = passwordEncoder.encode(rawPassword);
		userRepository.saveAndFlush(User.bootstrapAdmin(normalizedEmail, passwordHash));
		return true;
	}

	@Transactional(readOnly = true)
	public Optional<User> findByEmail(String email) {
		return userRepository.findByEmail(email.toLowerCase(Locale.ROOT));
	}

	@Transactional(readOnly = true)
	public User getById(Long id) {
		return userRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("User " + id + " not found"));
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
