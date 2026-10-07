package io.github.levij.orderflow;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointIT {

	@LocalServerPort
	private int port;

	@Test
	void healthEndpointReportsUp() {
		given()
				.port(port)
		.when()
				.get("/actuator/health")
		.then()
				.statusCode(200)
				.contentType(containsString("json"))
				.body("status", equalTo("UP"));
	}

}
