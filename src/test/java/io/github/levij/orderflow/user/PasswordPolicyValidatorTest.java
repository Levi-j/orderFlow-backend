package io.github.levij.orderflow.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyValidatorTest {

	private final PasswordPolicyValidator validator = new PasswordPolicyValidator();

	@Test
	void nullIsLeftToNotNull() {
		assertThat(validator.isValid(null, null)).isTrue();
	}

	@Test
	void fourteenCharactersAreTooShort() {
		assertThat(isValid("a".repeat(14))).isFalse();
	}

	@Test
	void fifteenAsciiCharactersAreValid() {
		assertThat(isValid("a".repeat(15))).isTrue();
	}

	@Test
	void seventyTwoAsciiBytesAreValidButSeventyThreeAreNot() {
		assertThat(isValid("a".repeat(72))).isTrue();
		assertThat(isValid("a".repeat(73))).isFalse();
	}

	@Test
	void multibytePasswordWithEnoughCodePointsWithinByteLimitIsValid() {
		assertThat(isValid("€".repeat(24))).isTrue();
	}

	@Test
	void multibytePasswordWithEnoughCodePointsOverByteLimitIsInvalid() {
		assertThat(isValid("€".repeat(25))).isFalse();
	}

	@Test
	void minimumCountsCodePointsNotUtf16Units() {
		String emoji = "😀".repeat(8);
		assertThat(emoji.length()).isEqualTo(16);
		assertThat(isValid(emoji)).isFalse();
	}

	@Test
	void fifteenEmojiAreLongEnoughAndWithinByteLimit() {
		assertThat(isValid("😀".repeat(15))).isTrue();
	}

	@Test
	void passwordIsNotTrimmed() {
		assertThat(isValid("a".repeat(14) + " ")).isTrue();
	}

	private boolean isValid(String password) {
		return validator.isValid(password, null);
	}
}
