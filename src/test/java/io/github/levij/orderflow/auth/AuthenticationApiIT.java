package io.github.levij.orderflow.auth;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import io.github.levij.orderflow.support.TestcontainersConfiguration;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AuthenticationApiIT {

	private static final String EMAIL = "jane.doe@example.com";
	private static final String PASSWORD = "correct horse battery staple";

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JwtEncoder jwtEncoder;

	@BeforeEach
	void cleanUsers() {
		jdbcTemplate.update("DELETE FROM users");
	}

	@Test
	void registerThenLoginThenReadCurrentUser() {
		Object userId = register(EMAIL, PASSWORD).then().statusCode(201).extract().path("id");

		String token = login(EMAIL, PASSWORD).then()
				.statusCode(200)
				.body("accessToken", not(emptyOrNullString()))
				.body("tokenType", equalTo("Bearer"))
				.body("expiresIn", equalTo(1800))
				.extract().path("accessToken");

		given().port(port)
				.auth().oauth2(token)
		.when()
				.get("/api/v1/users/me")
		.then()
				.statusCode(200)
				.body("id", equalTo(userId))
				.body("email", equalTo(EMAIL))
				.body("role", equalTo("CUSTOMER"))
				.body("createdAt", notNullValue())
				.body("$", not(hasKey("password")))
				.body("$", not(hasKey("passwordHash")));
	}

	@Test
	void loginIgnoresEmailLetterCase() {
		register("Mixed.Case@Example.com", PASSWORD).then().statusCode(201);

		login("MIXED.case@EXAMPLE.COM", PASSWORD).then()
				.statusCode(200)
				.body("accessToken", not(emptyOrNullString()));
	}

	@Test
	void wrongPasswordAndUnknownEmailGiveIdenticalResponses() {
		register(EMAIL, PASSWORD).then().statusCode(201);

		Response wrongPassword = login(EMAIL, "this is not the right password");
		Response unknownEmail = login("nobody@example.com", PASSWORD);

		for (Response response : new Response[] { wrongPassword, unknownEmail }) {
			response.then()
					.statusCode(401)
					.contentType(containsString("application/problem+json"))
					.header("WWW-Authenticate", containsString("Bearer"))
					.body("code", equalTo("INVALID_CREDENTIALS"))
					.body("detail", equalTo("Invalid email or password."));
		}
		Map<String, Object> wrongPasswordBody = wrongPassword.jsonPath().getMap("$");
		Map<String, Object> unknownEmailBody = unknownEmail.jsonPath().getMap("$");
		assertThat(wrongPasswordBody).isEqualTo(unknownEmailBody);
		assertThat(unknownEmail.asString()).doesNotContain("nobody@example.com");
	}

	@Test
	void passwordLongerThanBcryptLimitIsAnInvalidLoginNotAServerError() {
		register(EMAIL, PASSWORD).then().statusCode(201);

		login(EMAIL, "x".repeat(100)).then()
				.statusCode(401)
				.body("code", equalTo("INVALID_CREDENTIALS"));
	}

	@Test
	void invalidLoginBodyReturns400() {
		login("not-an-email", "").then()
				.statusCode(400)
				.body("code", equalTo("VALIDATION_FAILED"));
	}

	@Test
	void missingTokenReturns401() {
		given().port(port)
		.when()
				.get("/api/v1/users/me")
		.then()
				.statusCode(401)
				.contentType(containsString("application/problem+json"))
				.header("WWW-Authenticate", containsString("Bearer"))
				.body("code", equalTo("UNAUTHENTICATED"))
				.body("detail", equalTo("Authentication is required to access this resource."));
	}

	@Test
	void tamperedTokenReturns401() {
		register(EMAIL, PASSWORD).then().statusCode(201);
		String token = login(EMAIL, PASSWORD).path("accessToken");

		int index = token.lastIndexOf('.') + 10;
		char replacement = token.charAt(index) == 'A' ? 'B' : 'A';
		String tampered = token.substring(0, index) + replacement + token.substring(index + 1);

		assertUnauthenticated(tampered);
	}

	@Test
	void tokenSignedWithAnotherKeyReturns401() {
		byte[] otherKey = new byte[32];
		new SecureRandom().nextBytes(otherKey);
		JwtEncoder otherEncoder = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(otherKey, "HmacSHA256"))
				.algorithm(MacAlgorithm.HS256)
				.build();

		Instant now = Instant.now();
		assertUnauthenticated(encode(otherEncoder, "orderflow", now, now.plus(30, ChronoUnit.MINUTES)));
	}

	@Test
	void expiredTokenReturns401() {
		Instant issuedAt = Instant.now().minus(2, ChronoUnit.HOURS);
		Instant expiresAt = Instant.now().minus(10, ChronoUnit.MINUTES);

		assertUnauthenticated(encode(jwtEncoder, "orderflow", issuedAt, expiresAt));
	}

	@Test
	void tokenFromAnotherIssuerReturns401() {
		Instant now = Instant.now();

		assertUnauthenticated(encode(jwtEncoder, "someone-else", now, now.plus(30, ChronoUnit.MINUTES)));
	}

	@Test
	void productBrowsingAndRegistrationStayPublic() {
		given().port(port).get("/api/v1/products").then().statusCode(200);

		register("new.customer@example.com", PASSWORD).then()
				.statusCode(201)
				.body("role", equalTo("CUSTOMER"));
	}

	@Test
	void adminRoutesRejectAnonymousWith401AndCustomersWith403() {
		given().port(port)
		.when()
				.get("/api/v1/admin/products")
		.then()
				.statusCode(401)
				.body("code", equalTo("UNAUTHENTICATED"));

		register(EMAIL, PASSWORD).then().statusCode(201);
		String customerToken = login(EMAIL, PASSWORD).path("accessToken");

		given().port(port)
				.auth().oauth2(customerToken)
		.when()
				.get("/api/v1/admin/products")
		.then()
				.statusCode(403)
				.body("code", equalTo("ACCESS_DENIED"));
	}

	private void assertUnauthenticated(String token) {
		String body = given().port(port)
				.auth().oauth2(token)
		.when()
				.get("/api/v1/users/me")
		.then()
				.statusCode(401)
				.contentType(containsString("application/problem+json"))
				.header("WWW-Authenticate", containsString("Bearer"))
				.body("code", equalTo("UNAUTHENTICATED"))
				.body("detail", equalTo("Authentication is required to access this resource."))
				.extract().asString();

		assertThat(body).doesNotContain(token, "expired", "signature", "issuer", "Exception");
	}

	private static String encode(JwtEncoder encoder, String issuer, Instant issuedAt, Instant expiresAt) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(issuer)
				.subject("1")
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.claim("role", "CUSTOMER")
				.build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

	private Response register(String email, String password) {
		return given().port(port)
				.contentType(ContentType.JSON)
				.body(Map.of("email", email, "password", password))
		.when()
				.post("/api/v1/auth/register");
	}

	private Response login(String email, String password) {
		return given().port(port)
				.contentType(ContentType.JSON)
				.body(Map.of("email", email, "password", password))
		.when()
				.post("/api/v1/auth/login");
	}
}
