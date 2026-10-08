package io.github.levij.orderflow;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import io.github.levij.orderflow.support.TestcontainersConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OpenApiIT {

	@LocalServerPort
	private int port;

	@Test
	void apiDocsDescribeTheApiAndTheBearerScheme() {
		given().port(port)
		.when()
				.get("/v3/api-docs")
		.then()
				.statusCode(200)
				.contentType(containsString("json"))
				.body("openapi", notNullValue())
				.body("info.title", equalTo("OrderFlow API"))
				.body("info.version", equalTo("v1"))
				.body("components.securitySchemes.bearerAuth.type", equalTo("http"))
				.body("components.securitySchemes.bearerAuth.scheme", equalTo("bearer"))
				.body("components.securitySchemes.bearerAuth.bearerFormat", equalTo("JWT"))
				.body("paths.keySet()", hasItems(
						"/api/v1/auth/register",
						"/api/v1/auth/login",
						"/api/v1/users/me",
						"/api/v1/products",
						"/api/v1/products/{id}",
						"/api/v1/admin/products",
						"/api/v1/admin/products/{id}",
						"/api/v1/admin/inventory/{productId}",
						"/api/v1/admin/inventory/{productId}/adjustments",
						"/api/v1/admin/inventory/{productId}/movements",
						"/api/v1/orders",
						"/api/v1/orders/{id}"));
	}

	@Test
	void onlyProtectedOperationsRequireTheBearerToken() {
		given().port(port)
		.when()
				.get("/v3/api-docs")
		.then()
				.statusCode(200)
				.body("$", not(hasKey("security")))
				.body("paths.'/api/v1/auth/register'.post.security", nullValue())
				.body("paths.'/api/v1/auth/login'.post.security", nullValue())
				.body("paths.'/api/v1/products'.get.security", nullValue())
				.body("paths.'/api/v1/products/{id}'.get.security", nullValue())
				.body("paths.'/api/v1/users/me'.get.security[0]", hasKey("bearerAuth"))
				.body("paths.'/api/v1/admin/products'.get.security[0]", hasKey("bearerAuth"))
				.body("paths.'/api/v1/admin/products'.post.security[0]", hasKey("bearerAuth"))
				.body("paths.'/api/v1/admin/products/{id}'.put.security[0]", hasKey("bearerAuth"))
				.body("paths.'/api/v1/admin/inventory/{productId}'.get.security[0]", hasKey("bearerAuth"))
				.body("paths.'/api/v1/admin/inventory/{productId}/adjustments'.post.security[0]", hasKey("bearerAuth"))
				.body("paths.'/api/v1/admin/inventory/{productId}/movements'.get.security[0]", hasKey("bearerAuth"))
				.body("paths.'/api/v1/orders'.post.security[0]", hasKey("bearerAuth"))
				.body("paths.'/api/v1/orders'.get.security[0]", hasKey("bearerAuth"))
				.body("paths.'/api/v1/orders/{id}'.get.security[0]", hasKey("bearerAuth"));
	}

	@Test
	void createOrderRequestOnlyAcceptsProductIdsAndQuantities() {
		given().port(port)
		.when()
				.get("/v3/api-docs")
		.then()
				.statusCode(200)
				.body("paths.'/api/v1/orders'.post.requestBody.content.'application/json'.schema.'$ref'",
						equalTo("#/components/schemas/CreateOrderRequest"))
				.body("components.schemas.CreateOrderRequest.properties.keySet()", contains("items"))
				.body("components.schemas.CreateOrderRequest.properties.items.items.'$ref'",
						equalTo("#/components/schemas/OrderItemRequest"))
				.body("components.schemas.OrderItemRequest.properties.keySet()",
						containsInAnyOrder("productId", "quantity"));
	}

	@Test
	void swaggerUiLoadsWithoutAuthentication() {
		given().port(port)
		.when()
				.get("/swagger-ui/index.html")
		.then()
				.statusCode(200)
				.contentType(containsString("text/html"))
				.body(containsString("swagger-ui"));

		given().port(port)
		.when()
				.get("/swagger-ui.html")
		.then()
				.statusCode(200)
				.contentType(containsString("text/html"));
	}
}
