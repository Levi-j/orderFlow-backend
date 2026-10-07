package io.github.levij.orderflow.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

	private static final String EMAIL = "Admin@Example.com";
	private static final String PASSWORD = "bootstrap admin password";

	private static ValidatorFactory validatorFactory;
	private static Validator validator;

	@Mock
	private UserService userService;

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
	void doesNothingWhenNoAdminIsConfigured() {
		bootstrap("", "").run(null);
		bootstrap(null, null).run(null);

		verifyNoInteractions(userService);
	}

	@Test
	void createsAdminThroughUserServiceWhenFullyConfigured() {
		bootstrap(EMAIL, PASSWORD).run(null);

		verify(userService).ensureBootstrapAdmin(EMAIL, PASSWORD);
	}

	@Test
	void emailWithoutPasswordStopsStartup() {
		assertThatThrownBy(() -> bootstrap(EMAIL, "").run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("ORDERFLOW_ADMIN_EMAIL")
				.hasMessageContaining("ORDERFLOW_ADMIN_PASSWORD")
				.hasMessageNotContaining(EMAIL);

		verifyNoInteractions(userService);
	}

	@Test
	void passwordWithoutEmailStopsStartup() {
		assertThatThrownBy(() -> bootstrap("", PASSWORD).run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageNotContaining(PASSWORD);

		verifyNoInteractions(userService);
	}

	@Test
	void invalidEmailStopsStartupWithoutRevealingIt() {
		assertThatThrownBy(() -> bootstrap("not-an-email", PASSWORD).run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("orderflow.admin.email")
				.hasMessageNotContaining("not-an-email");

		verifyNoInteractions(userService);
	}

	@Test
	void passwordMustFollowTheRegistrationPolicy() {
		String tooShort = "only 14 chars!";
		String tooManyBytes = "€".repeat(25); // 25 characters, but 75 bytes in UTF-8

		for (String password : new String[] { tooShort, tooManyBytes }) {
			assertThatThrownBy(() -> bootstrap(EMAIL, password).run(null))
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("orderflow.admin.password")
					.hasMessageNotContaining(password);
		}

		verifyNoInteractions(userService);
	}

	private AdminBootstrap bootstrap(String email, String password) {
		return new AdminBootstrap(new AdminProperties(email, password), userService, validator);
	}
}
