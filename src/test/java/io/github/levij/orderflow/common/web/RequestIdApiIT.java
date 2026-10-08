package io.github.levij.orderflow.common.web;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.levij.orderflow.auth.JwtTokenService;
import io.github.levij.orderflow.support.DatabaseCleanup;
import io.github.levij.orderflow.support.TestcontainersConfiguration;
import io.github.levij.orderflow.user.Role;
import io.github.levij.orderflow.user.UserService;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class RequestIdApiIT {

	private static final String UUID_FORMAT = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
	private static final String PASSWORD = "request id customer password";

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private UserService userService;

	@Autowired
	private JwtTokenService jwtTokenService;

	@BeforeEach
	void setUp() {
		DatabaseCleanup.deleteAllData(jdbcTemplate);
	}

	@AfterEach
	void cleanUp() {
		DatabaseCleanup.deleteAllData(jdbcTemplate);
	}

	@Test
	void suppliedIdIsReturnedOnASuccessfulResponseWithoutChangingTheBody() {
		given().port(port)
				.header(RequestIdFilter.HEADER, "demo-123")
		.when()
				.get("/actuator/health")
		.then()
				.statusCode(200)
				.header(RequestIdFilter.HEADER, "demo-123")
				.body("$", not(hasKey("requestId")));
	}

	@Test
	void missingIdIsGenerated() {
		String generated = given().port(port).get("/actuator/health").then()
				.statusCode(200)
				.extract().header(RequestIdFilter.HEADER);

		assertThat(generated).matches(UUID_FORMAT);
	}

	@Test
	void invalidIdIsReplacedAndNeverEchoed() {
		for (String invalidId : List.of("bad id!", "x".repeat(65))) {
			Response response = given().port(port)
					.header(RequestIdFilter.HEADER, invalidId)
				.when()
					.get("/actuator/health");

			response.then().statusCode(200);
			assertThat(response.header(RequestIdFilter.HEADER)).matches(UUID_FORMAT).isNotEqualTo(invalidId);
			assertThat(response.asString()).doesNotContain(invalidId);
		}
	}

	@Test
	void validationAndMalformedRequestErrorsCarryTheRequestId() {
		Response invalid = postJson("/api/v1/auth/register", "validation-400",
				Map.of("email", "not-an-email", "password", "short"));
		invalid.then().statusCode(400).body("code", equalTo("VALIDATION_FAILED"));
		assertCorrelated(invalid, "validation-400");

		Response malformed = given().port(port)
				.header(RequestIdFilter.HEADER, "malformed-400")
				.contentType(ContentType.JSON)
				.body("{ \"email\": ")
			.when()
				.post("/api/v1/auth/login");
		malformed.then().statusCode(400).body("code", equalTo("MALFORMED_REQUEST"));
		assertCorrelated(malformed, "malformed-400");
	}

	@Test
	void unauthenticatedRequestIsCorrelated() {
		Response response = given().port(port)
				.header(RequestIdFilter.HEADER, "auth-401")
			.when()
				.get("/api/v1/users/me");

		response.then()
				.statusCode(401)
				.header("WWW-Authenticate", containsString("Bearer"))
				.body("code", equalTo("UNAUTHENTICATED"));
		assertCorrelated(response, "auth-401");
	}

	@Test
	void forbiddenRequestIsCorrelated() {
		long customerId = userService.registerCustomer("request.id.customer@example.com", PASSWORD).getId();

		Response response = given().port(port)
				.header(RequestIdFilter.HEADER, "authz-403")
				.auth().oauth2(jwtTokenService.issue(customerId, Role.CUSTOMER).value())
			.when()
				.get("/api/v1/admin/products");

		response.then().statusCode(403).body("code", equalTo("ACCESS_DENIED"));
		assertCorrelated(response, "authz-403");
	}

	@Test
	void notFoundIsCorrelated() {
		Response response = given().port(port)
				.header(RequestIdFilter.HEADER, "missing-404")
			.when()
				.get("/api/v1/products/{id}", 999_999);

		response.then().statusCode(404).body("code", equalTo("RESOURCE_NOT_FOUND"));
		assertCorrelated(response, "missing-404");
	}

	@Test
	void businessConflictIsCorrelated() {
		userService.registerCustomer("taken@example.com", PASSWORD);

		Response response = postJson("/api/v1/auth/register", "conflict-409",
				Map.of("email", "taken@example.com", "password", PASSWORD));

		response.then().statusCode(409).body("code", equalTo("EMAIL_ALREADY_REGISTERED"));
		assertCorrelated(response, "conflict-409");
	}

	@Test
	void logsWrittenWhileHandlingARequestCarryItsId(CapturedOutput output) {
		postJson("/api/v1/auth/register", "correlation-demo-123",
				Map.of("email", "correlated@example.com", "password", PASSWORD)).then().statusCode(201);

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			List<String> requestLogs = output.getOut().lines()
					.filter(line -> line.contains("[correlation-demo-123]"))
					.toList();
			assertThat(requestLogs).anyMatch(line -> line.contains("eventName=user.registered"));
			assertThat(requestLogs).filteredOn(line -> line.contains("eventName=http.request"))
					.singleElement()
					.satisfies(line -> assertThat(line).contains("method=POST", "path=/api/v1/auth/register ", "status=201"));
		});
	}

	private Response postJson(String path, String requestId, Object body) {
		return given().port(port)
				.header(RequestIdFilter.HEADER, requestId)
				.contentType(ContentType.JSON)
				.body(body)
			.when()
				.post(path);
	}

	private static void assertCorrelated(Response response, String requestId) {
		assertThat(response.header(RequestIdFilter.HEADER)).isEqualTo(requestId);
		assertThat(response.jsonPath().getString("requestId")).isEqualTo(requestId);
	}
}
