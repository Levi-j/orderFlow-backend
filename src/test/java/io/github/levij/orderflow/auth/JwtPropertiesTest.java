package io.github.levij.orderflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class JwtPropertiesTest {

	private static ValidatorFactory validatorFactory;
	private static Validator validator;

	@BeforeAll
	static void createValidator() {
		validatorFactory = Validation.buildDefaultValidatorFactory();
		validator = validatorFactory.getValidator();
	}

	@AfterAll
	static void closeValidator() {
		validatorFactory.close();
	}

	@Test
	void secretOf32BytesIsAccepted() {
		assertThat(validate("a".repeat(32))).isEmpty();
	}

	@Test
	void secretOf31BytesIsRejectedWithoutRevealingIt() {
		String secret = "s".repeat(31);

		Set<ConstraintViolation<JwtProperties>> violations = validate(secret);

		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).doesNotContain(secret);
	}

	@Test
	void lengthIsMeasuredInUtf8BytesNotCharacters() {
		// 11 euro signs: only 11 characters, but 33 bytes in UTF-8.
		assertThat(validate("€".repeat(11))).isEmpty();
	}

	@Test
	void blankOrMissingSecretIsRejected() {
		assertThat(validate("")).isNotEmpty();
		assertThat(validate("   ")).isNotEmpty();
		assertThat(validate(null)).isNotEmpty();
	}

	private static Set<ConstraintViolation<JwtProperties>> validate(String secret) {
		return validator.validate(new JwtProperties(secret, "orderflow", Duration.ofMinutes(30)));
	}
}
