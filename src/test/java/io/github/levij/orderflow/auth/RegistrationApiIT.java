package io.github.levij.orderflow.auth;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.levij.orderflow.support.TestcontainersConfiguration;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RegistrationApiIT {

	private static final String VALID_PASSWORD = "correct horse battery staple";

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void cleanUsers() {
		jdbcTemplate.update("DELETE FROM users");
	}

	@Test
	void registrationCreatesCustomerWithLowercaseEmailAndBcryptHash() {
		String responseBody = register("Jane.Doe@Example.COM", VALID_PASSWORD)
				.then()
				.statusCode(201)
				.body("id", notNullValue())
				.body("email", equalTo("jane.doe@example.com"))
				.body("role", equalTo("CUSTOMER"))
				.body("createdAt", notNullValue())
				.body("$", not(hasKey("password")))
				.body("$", not(hasKey("passwordHash")))
				.extract().asString();

		assertThat(responseBody).doesNotContain(VALID_PASSWORD).doesNotContain("$2");

		Map<String, Object> row = jdbcTemplate.queryForMap("SELECT email, role, password_hash FROM users");
		String storedHash = (String) row.get("password_hash");
		assertThat(row.get("email")).isEqualTo("jane.doe@example.com");
		assertThat(row.get("role")).isEqualTo("CUSTOMER");
		assertThat(storedHash).isNotEqualTo(VALID_PASSWORD).startsWith("$2");
		assertThat(passwordEncoder.matches(VALID_PASSWORD, storedHash)).isTrue();
	}

	@Test
	void duplicateEmailIgnoringCaseReturns409() {
		register("Example.User@example.com", VALID_PASSWORD).then().statusCode(201);

		String responseBody = register("example.user@EXAMPLE.com", VALID_PASSWORD)
				.then()
				.statusCode(409)
				.contentType(containsString("application/problem+json"))
				.body("code", equalTo("EMAIL_ALREADY_REGISTERED"))
				.extract().asString();

		assertThat(responseBody).doesNotContain("SQL", "uk_users_email", "duplicate key", "postgresql", "Exception");
		assertThat(userCount()).isEqualTo(1);
	}

	@Test
	void tooShortPasswordReturns400() {
		register("short@example.com", "only fourteen!").then()
				.statusCode(400)
				.body("code", equalTo("VALIDATION_FAILED"))
				.body("errors.field", hasItem("password"));

		assertThat(userCount()).isZero();
	}

	@Test
	void passwordOverSeventyTwoUtf8BytesReturns400() {
		String password = "€".repeat(25);
		assertThat(password.codePointCount(0, password.length())).isGreaterThanOrEqualTo(15);

		register("bytes@example.com", password).then()
				.statusCode(400)
				.body("code", equalTo("VALIDATION_FAILED"))
				.body("errors.field", hasItem("password"));

		assertThat(userCount()).isZero();
	}

	@Test
	void roleInRequestCannotCreateAdmin() {
		given().port(port)
				.contentType(ContentType.JSON)
				.body("""
						{"email": "sneaky@example.com", "password": "%s", "role": "ADMIN"}
						""".formatted(VALID_PASSWORD))
		.when()
				.post("/api/v1/auth/register")
		.then()
				.statusCode(201)
				.body("role", equalTo("CUSTOMER"));

		String storedRole = jdbcTemplate.queryForObject("SELECT role FROM users", String.class);
		assertThat(storedRole).isEqualTo("CUSTOMER");
	}

	@Test
	void invalidEmailReturns400() {
		register("not-an-email", VALID_PASSWORD).then()
				.statusCode(400)
				.body("code", equalTo("VALIDATION_FAILED"))
				.body("errors.field", hasItem("email"));

		assertThat(userCount()).isZero();
	}

	@Test
	void validationErrorDoesNotEchoThePassword() {
		String password = "too short";

		String responseBody = register("echo@example.com", password).then()
				.statusCode(400)
				.extract().asString();

		assertThat(responseBody).doesNotContain(password);
	}

	private Response register(String email, String password) {
		return given().port(port)
				.contentType(ContentType.JSON)
				.body(Map.of("email", email, "password", password))
		.when()
				.post("/api/v1/auth/register");
	}

	private int userCount() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
	}
}
