package io.github.levij.orderflow.auth;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

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
import io.github.levij.orderflow.user.AdminBootstrap;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"orderflow.admin.email=" + AuthorizationApiIT.ADMIN_EMAIL,
		"orderflow.admin.password=" + AuthorizationApiIT.ADMIN_PASSWORD })
@Import(TestcontainersConfiguration.class)
class AuthorizationApiIT {

	static final String ADMIN_EMAIL = "Bootstrap.Admin@Example.com";
	static final String ADMIN_PASSWORD = "test-only bootstrap admin password";

	private static final String CUSTOMER_EMAIL = "customer@example.com";
	private static final String CUSTOMER_PASSWORD = "correct horse battery staple";

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AdminBootstrap adminBootstrap;

	@BeforeEach
	void cleanTestData() {
		jdbcTemplate.update("DELETE FROM users WHERE role <> 'ADMIN'");
		jdbcTemplate.update("DELETE FROM products");
	}

	@Test
	void bootstrapAdminIsStoredWithLowercaseEmailAndBcryptHash() {
		Map<String, Object> admin = jdbcTemplate.queryForMap(
				"SELECT email, role, password_hash FROM users WHERE role = 'ADMIN'");
		String storedHash = (String) admin.get("password_hash");

		assertThat(admin.get("email")).isEqualTo("bootstrap.admin@example.com");
		assertThat(admin.get("role")).isEqualTo("ADMIN");
		assertThat(storedHash).startsWith("$2").isNotEqualTo(ADMIN_PASSWORD);
		assertThat(passwordEncoder.matches(ADMIN_PASSWORD, storedHash)).isTrue();
	}

	@Test
	void adminLogsInThroughTheNormalLoginAndHasTheAdminRole() {
		String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD).then()
				.statusCode(200)
				.body("tokenType", equalTo("Bearer"))
				.body("expiresIn", equalTo(1800))
				.extract().path("accessToken");

		given().port(port)
				.auth().oauth2(adminToken)
		.when()
				.get("/api/v1/users/me")
		.then()
				.statusCode(200)
				.body("email", equalTo("bootstrap.admin@example.com"))
				.body("role", equalTo("ADMIN"));
	}

	@Test
	void adminCanUseAdminRoutes() {
		String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD).path("accessToken");

		given().port(port)
				.auth().oauth2(adminToken)
		.when()
				.get("/api/v1/admin/products")
		.then()
				.statusCode(200);

		given().port(port)
				.auth().oauth2(adminToken)
				.contentType(ContentType.JSON)
				.body(Map.of("sku", "ADMIN-ONLY-1", "name", "Created by admin", "price", 10))
		.when()
				.post("/api/v1/admin/products")
		.then()
				.statusCode(201);
	}

	@Test
	void customerIsForbiddenFromAdminRoutesButCanReadOwnProfile() {
		registerCustomer();
		String customerToken = login(CUSTOMER_EMAIL, CUSTOMER_PASSWORD).path("accessToken");

		String responseBody = given().port(port)
				.auth().oauth2(customerToken)
		.when()
				.get("/api/v1/admin/products")
		.then()
				.statusCode(403)
				.contentType(containsString("application/problem+json"))
				.header("WWW-Authenticate", nullValue())
				.body("code", equalTo("ACCESS_DENIED"))
				.body("detail", equalTo("You do not have permission to access this resource."))
				.extract().asString();

		assertThat(responseBody).doesNotContain(customerToken, "ROLE_", "ADMIN", "Exception");

		given().port(port)
				.auth().oauth2(customerToken)
				.contentType(ContentType.JSON)
				.body(Map.of("sku", "CUSTOMER-1", "name", "Not allowed", "price", 10))
		.when()
				.post("/api/v1/admin/products")
		.then()
				.statusCode(403);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class)).isZero();

		given().port(port)
				.auth().oauth2(customerToken)
		.when()
				.get("/api/v1/users/me")
		.then()
				.statusCode(200)
				.body("role", equalTo("CUSTOMER"));
	}

	@Test
	void anonymousAdminRequestIsStillUnauthenticated() {
		given().port(port)
		.when()
				.get("/api/v1/admin/products")
		.then()
				.statusCode(401)
				.header("WWW-Authenticate", containsString("Bearer"))
				.body("code", equalTo("UNAUTHENTICATED"));
	}

	@Test
	void productBrowsingStaysPublic() {
		given().port(port).get("/api/v1/products").then().statusCode(200);
	}

	@Test
	void runningTheBootstrapAgainChangesNothing() {
		Map<String, Object> before = jdbcTemplate.queryForMap(
				"SELECT id, password_hash, updated_at FROM users WHERE role = 'ADMIN'");

		adminBootstrap.run(null);

		Map<String, Object> after = jdbcTemplate.queryForMap(
				"SELECT id, password_hash, updated_at FROM users WHERE role = 'ADMIN'");
		assertThat(after).isEqualTo(before);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM users WHERE email = 'bootstrap.admin@example.com'", Integer.class)).isEqualTo(1);
	}

	private void registerCustomer() {
		given().port(port)
				.contentType(ContentType.JSON)
				.body(Map.of("email", CUSTOMER_EMAIL, "password", CUSTOMER_PASSWORD))
		.when()
				.post("/api/v1/auth/register")
		.then()
				.statusCode(201);
	}

	private Response login(String email, String password) {
		return given().port(port)
				.contentType(ContentType.JSON)
				.body(Map.of("email", email, "password", password))
		.when()
				.post("/api/v1/auth/login");
	}
}
