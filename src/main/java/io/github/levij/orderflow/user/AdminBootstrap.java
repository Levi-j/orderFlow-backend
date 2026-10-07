package io.github.levij.orderflow.user;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

@Component
@EnableConfigurationProperties(AdminProperties.class)
public class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final AdminProperties adminProperties;
	private final UserService userService;
	private final Validator validator;

	public AdminBootstrap(AdminProperties adminProperties, UserService userService, Validator validator) {
		this.adminProperties = adminProperties;
		this.userService = userService;
		this.validator = validator;
	}

	@Override
	public void run(ApplicationArguments args) {
		boolean hasEmail = adminProperties.hasEmail();
		boolean hasPassword = adminProperties.hasPassword();

		if (!hasEmail && !hasPassword) {
			return;
		}
		if (hasEmail != hasPassword) {
			throw new IllegalStateException(
					"ORDERFLOW_ADMIN_EMAIL and ORDERFLOW_ADMIN_PASSWORD must be set together, or both left empty.");
		}

		Set<ConstraintViolation<AdminProperties>> violations = validator.validate(adminProperties);
		if (!violations.isEmpty()) {
			String problems = violations.stream()
					.sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
					.map(violation -> "orderflow.admin." + violation.getPropertyPath() + " " + violation.getMessage())
					.collect(Collectors.joining("; "));
			throw new IllegalStateException("Invalid bootstrap admin configuration: " + problems);
		}

		if (userService.ensureBootstrapAdmin(adminProperties.email(), adminProperties.password())) {
			log.info("Bootstrap administrator account created");
		}
	}
}
