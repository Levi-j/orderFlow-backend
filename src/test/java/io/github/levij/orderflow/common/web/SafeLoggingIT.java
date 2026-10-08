package io.github.levij.orderflow.common.web;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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

import io.github.levij.orderflow.inventory.InventoryService;
import io.github.levij.orderflow.inventory.MovementReason;
import io.github.levij.orderflow.product.ProductService;
import io.github.levij.orderflow.product.dto.CreateProductRequest;
import io.github.levij.orderflow.support.DatabaseCleanup;
import io.github.levij.orderflow.support.TestcontainersConfiguration;
import io.github.levij.orderflow.user.UserService;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class SafeLoggingIT {

	private static final String EMAIL = "logging.marker.customer@example.com";
	private static final String PASSWORD = "Marker-Password-Never-Logged-1";

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private UserService userService;

	@Autowired
	private ProductService productService;

	@Autowired
	private InventoryService inventoryService;

	@BeforeEach
	void setUp() {
		DatabaseCleanup.deleteAllData(jdbcTemplate);
	}

	@AfterEach
	void cleanUp() {
		DatabaseCleanup.deleteAllData(jdbcTemplate);
	}

	@Test
	void registrationLogsTheNewUserIdButNeverTheEmailOrPassword(CapturedOutput output) {
		long userId = post("/api/v1/auth/register", Map.of("email", EMAIL, "password", PASSWORD)).then()
				.statusCode(201)
				.extract().jsonPath().getLong("id");
		String passwordHash = jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE id = ?",
				String.class, userId);

		assertThat(output.getOut())
				.contains("eventName=user.registered userId=" + userId + " role=CUSTOMER")
				.doesNotContain(EMAIL, PASSWORD, passwordHash);
	}

	@Test
	void failedLoginsAreLoggedWithoutAnyCredentials(CapturedOutput output) {
		userService.registerCustomer(EMAIL, PASSWORD);

		post("/api/v1/auth/login", Map.of("email", EMAIL, "password", "Wrong-Password-Marker-2")).then().statusCode(401);
		post("/api/v1/auth/login", Map.of("email", "unknown.marker@example.com", "password", "Unknown-Password-Marker-3"))
				.then().statusCode(401);

		List<String> failures = output.getOut().lines().filter(line -> line.contains("eventName=auth.login_failed")).toList();
		assertThat(failures).hasSize(2)
				.allMatch(line -> line.contains(" WARN ") && line.endsWith("Login failed eventName=auth.login_failed"));
		assertThat(output.getOut()).doesNotContain(EMAIL, PASSWORD, "Wrong-Password-Marker-2",
				"unknown.marker@example.com", "Unknown-Password-Marker-3");
	}

	@Test
	void bearerTokensNeverAppearInLogs(CapturedOutput output) {
		userService.registerCustomer(EMAIL, PASSWORD);
		String token = post("/api/v1/auth/login", Map.of("email", EMAIL, "password", PASSWORD)).then()
				.statusCode(200)
				.extract().path("accessToken");

		given().port(port).auth().oauth2(token).get("/api/v1/users/me").then().statusCode(200);
		given().port(port).auth().oauth2("Marker.Invalid.Token").get("/api/v1/users/me").then().statusCode(401);

		assertThat(output.getOut())
				.contains("path=/api/v1/users/me status=200", "path=/api/v1/users/me status=401")
				.doesNotContain(token, "Marker.Invalid.Token");
	}

	@Test
	void idempotentReplayIsLoggedByOrderIdWithoutTheKeyOrRequestHash(CapturedOutput output) {
		userService.ensureBootstrapAdmin("logging.admin@example.com", "logging admin password");
		long adminId = userService.findByEmail("logging.admin@example.com").orElseThrow().getId();
		long productId = productService.create(new CreateProductRequest("LOG-1", "Logged product", null,
				new BigDecimal("5.00"))).getId();
		inventoryService.adjust(productId, 10, MovementReason.RESTOCK, null, adminId);
		userService.registerCustomer(EMAIL, PASSWORD);
		String token = post("/api/v1/auth/login", Map.of("email", EMAIL, "password", PASSWORD)).path("accessToken");
		Map<String, Object> body = Map.of("items", List.of(Map.of("productId", productId, "quantity", 2)));

		long orderId = placeOrder(token, body).then().statusCode(201).extract().jsonPath().getLong("id");
		placeOrder(token, body).then().statusCode(201).header("Idempotent-Replayed", "true");
		String requestHash = jdbcTemplate.queryForObject("SELECT request_hash FROM orders WHERE id = ?",
				String.class, orderId);

		assertThat(output.getOut())
				.contains("eventName=order.placed orderId=" + orderId, "eventName=order.idempotent_replay orderId=" + orderId)
				.doesNotContain("checkout-marker-key-77", requestHash);
	}

	private Response post(String path, Object body) {
		return given().port(port)
				.contentType(ContentType.JSON)
				.body(body)
			.when()
				.post(path);
	}

	private Response placeOrder(String token, Object body) {
		return given().port(port)
				.auth().oauth2(token)
				.header("Idempotency-Key", "checkout-marker-key-77")
				.contentType(ContentType.JSON)
				.body(body)
			.when()
				.post("/api/v1/orders");
	}
}
