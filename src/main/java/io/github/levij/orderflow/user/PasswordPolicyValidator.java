package io.github.levij.orderflow.user;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordPolicyValidator implements ConstraintValidator<PasswordPolicy, String> {

	static final int MIN_CODE_POINTS = 15;

	static final int MAX_UTF8_BYTES = 72;

	@Override
	public boolean isValid(String password, ConstraintValidatorContext context) {
		if (password == null) {
			return true;
		}
		int codePoints = password.codePointCount(0, password.length());
		int utf8Bytes = password.getBytes(StandardCharsets.UTF_8).length;
		return codePoints >= MIN_CODE_POINTS && utf8Bytes <= MAX_UTF8_BYTES;
	}
}
