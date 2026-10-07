package io.github.levij.orderflow.product;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.levij.orderflow.auth.JwtTokenService;
import io.github.levij.orderflow.support.TestcontainersConfiguration;
import io.github.levij.orderflow.user.Role;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ProductApiIT {

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JwtTokenService jwtTokenService;

	private String accessToken;

	@BeforeEach
	void setUp() {
		jdbcTemplate.update("DELETE FROM products");
		accessToken = jwtTokenService.issue(1L, Role.CUSTOMER).value();
	}

	@Test
	void adminCanCreateProductAndReadItBack() {
		Response created = createProduct("LAPTOP-15", "Laptop 15", "A 15 inch laptop", "999.99");

		created.then()
				.statusCode(201)
				.header("Location", notNullValue())
				.body("id", notNullValue())
				.body("sku", equalTo("LAPTOP-15"))
				.body("name", equalTo("Laptop 15"))
				.body("description", equalTo("A 15 inch laptop"))
				.body("price", equalTo(999.99f))
				.body("active", equalTo(true))
				.body("createdAt", notNullValue())
				.body("updatedAt", notNullValue());

		authenticated()
				.get(created.header("Location"))
		.then()
				.statusCode(200)
				.body("id", equalTo(created.path("id")))
				.body("sku", equalTo("LAPTOP-15"))
				.body("price", equalTo(999.99f))
				.body("createdAt", equalTo(created.path("createdAt")));
	}

	@Test
	void publicListShowsActiveProductsInPageResponseShape() {
		createProduct("BOOK-1", "Book", null, "12.50");

		given().port(port)
		.when()
				.get("/api/v1/products")
		.then()
				.statusCode(200)
				.body("content", hasSize(1))
				.body("content[0].sku", equalTo("BOOK-1"))
				.body("page", equalTo(0))
				.body("size", equalTo(20))
				.body("totalElements", equalTo(1))
				.body("totalPages", equalTo(1));
	}

	@Test
	void listIsPaginatedAndSorted() {
		createProduct("ITEM-C", "Charlie", null, "3.00");
		createProduct("ITEM-A", "Alpha", null, "1.00");
		createProduct("ITEM-B", "Bravo", null, "2.00");

		given().port(port)
				.queryParam("page", 0)
				.queryParam("size", 2)
				.queryParam("sort", "name,asc")
		.when()
				.get("/api/v1/products")
		.then()
				.statusCode(200)
				.body("content.name", contains("Alpha", "Bravo"))
				.body("size", equalTo(2))
				.body("totalElements", equalTo(3))
				.body("totalPages", equalTo(2));
	}

	@Test
	void publicListHidesInactiveProducts() {
		createProduct("ACTIVE-1", "Active product", null, "5.00");
		insertInactiveProduct("INACTIVE-1");

		given().port(port)
		.when()
				.get("/api/v1/products")
		.then()
				.statusCode(200)
				.body("content.sku", hasItem("ACTIVE-1"))
				.body("content.sku", not(hasItem("INACTIVE-1")));
	}

	@Test
	void publicDetailReturns404ForInactiveProduct() {
		long inactiveId = insertInactiveProduct("INACTIVE-1");

		given().port(port)
		.when()
				.get("/api/v1/products/{id}", inactiveId)
		.then()
				.statusCode(404);
	}

	@Test
	void adminCanStillReadInactiveProduct() {
		long inactiveId = insertInactiveProduct("INACTIVE-1");

		authenticated()
		.when()
				.get("/api/v1/admin/products/{id}", inactiveId)
		.then()
				.statusCode(200)
				.body("sku", equalTo("INACTIVE-1"))
				.body("active", equalTo(false));

		authenticated()
		.when()
				.get("/api/v1/admin/products")
		.then()
				.statusCode(200)
				.body("content.sku", hasItem("INACTIVE-1"));
	}

	@Test
	void missingProductReturns404() {
		given().port(port)
		.when()
				.get("/api/v1/products/{id}", 999999)
		.then()
				.statusCode(404);

		authenticated()
		.when()
				.get("/api/v1/admin/products/{id}", 999999)
		.then()
				.statusCode(404);
	}

	@Test
	void skuIsNormalizedToUppercase() {
		Response created = createProduct("keyboard-123", "Keyboard", null, "49.90");

		created.then()
				.statusCode(201)
				.body("sku", equalTo("KEYBOARD-123"));

		authenticated()
				.get(created.header("Location"))
		.then()
				.statusCode(200)
				.body("sku", equalTo("KEYBOARD-123"));
	}

	@Test
	void duplicateSkuReturns409WithoutDatabaseDetails() {
		createProduct("DUP-1", "First", null, "1.00").then().statusCode(201);

		String responseBody = createProduct("dup-1", "Second", null, "2.00")
				.then()
				.statusCode(409)
				.contentType(containsString("application/problem+json"))
				.body("status", equalTo(409))
				.body("code", equalTo("DUPLICATE_SKU"))
				.extract().asString();

		assertThat(responseBody).doesNotContain("SQL", "uk_products_sku", "duplicate key", "postgresql", "Exception");
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class)).isEqualTo(1);
	}

	@Test
	void invalidProductReturns400WithFieldErrors() {
		authenticated()
				.contentType(ContentType.JSON)
				.body("""
						{"sku": "bad sku!", "name": "", "price": 0}
						""")
		.when()
				.post("/api/v1/admin/products")
		.then()
				.statusCode(400)
				.contentType(containsString("application/problem+json"))
				.body("code", equalTo("VALIDATION_FAILED"))
				.body("errors.field", hasItems("sku", "name", "price"))
				.body("errors.message", everyItem(notNullValue()));

		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class)).isZero();
	}

	@Test
	void malformedJsonReturns400WithoutJavaDetails() {
		String responseBody = authenticated()
				.contentType(ContentType.JSON)
				.body("{ \"sku\": ")
		.when()
				.post("/api/v1/admin/products")
		.then()
				.statusCode(400)
				.body("code", equalTo("MALFORMED_REQUEST"))
				.extract().asString();

		assertThat(responseBody).doesNotContain("Exception", "jackson", "java.", "at io.");
	}

	@Test
	void adminCanUpdateProductButNotItsSku() {
		Response created = createProduct("update-me", "Old name", "Old description", "10.00");
		Object id = created.path("id");

		Response updated = updateProduct(id, "New name", "New description", "25.50", true);

		updated.then()
				.statusCode(200)
				.body("id", equalTo(id))
				.body("sku", equalTo("UPDATE-ME"))
				.body("name", equalTo("New name"))
				.body("description", equalTo("New description"))
				.body("price", equalTo(25.5f))
				.body("active", equalTo(true))
				.body("createdAt", equalTo(created.path("createdAt")));

		Instant createdAt = Instant.parse(created.path("createdAt"));
		Instant updatedAt = Instant.parse(updated.path("updatedAt"));
		assertThat(updatedAt).isAfter(createdAt);

		authenticated()
		.when()
				.get("/api/v1/admin/products/{id}", id)
		.then()
				.statusCode(200)
				.body("name", equalTo("New name"))
				.body("price", equalTo(25.5f));
	}

	@Test
	void deactivatedProductDisappearsFromPublicApiAndCanBeReactivated() {
		Object id = createProduct("TOGGLE-1", "Toggle", null, "5.00").path("id");

		updateProduct(id, "Toggle", null, "5.00", false).then().statusCode(200).body("active", equalTo(false));

		given().port(port).get("/api/v1/products/{id}", id).then().statusCode(404);
		given().port(port).get("/api/v1/products").then()
				.statusCode(200)
				.body("content.sku", not(hasItem("TOGGLE-1")));
		authenticated().get("/api/v1/admin/products/{id}", id).then()
				.statusCode(200)
				.body("active", equalTo(false));

		updateProduct(id, "Toggle", null, "5.00", true).then().statusCode(200).body("active", equalTo(true));

		given().port(port).get("/api/v1/products/{id}", id).then()
				.statusCode(200)
				.body("sku", equalTo("TOGGLE-1"));
	}

	@Test
	void updatingUnknownProductReturns404() {
		updateProduct(999999, "Name", null, "1.00", true)
				.then()
				.statusCode(404)
				.contentType(containsString("application/problem+json"))
				.body("code", equalTo("RESOURCE_NOT_FOUND"));
	}

	@Test
	void invalidUpdateReturns400() {
		Object id = createProduct("UPD-BAD", "Name", null, "1.00").path("id");

		authenticated()
				.contentType(ContentType.JSON)
				.body("""
						{"name": "", "price": 1.00}
						""")
		.when()
				.put("/api/v1/admin/products/{id}", id)
		.then()
				.statusCode(400)
				.body("code", equalTo("VALIDATION_FAILED"))
				.body("errors.field", hasItems("name", "active"));
	}

	@Test
	void unknownSortPropertyReturns400() {
		given().port(port)
				.queryParam("sort", "nonsense")
		.when()
				.get("/api/v1/products")
		.then()
				.statusCode(400)
				.body("code", equalTo("MALFORMED_REQUEST"));
	}

	private RequestSpecification authenticated() {
		return given().port(port).auth().oauth2(accessToken);
	}

	private Response updateProduct(Object id, String name, String description, String price, boolean active) {
		String descriptionJson = description == null ? "null" : "\"" + description + "\"";
		String body = """
				{"name": "%s", "description": %s, "price": %s, "active": %s}
				""".formatted(name, descriptionJson, price, active);

		return authenticated()
				.contentType(ContentType.JSON)
				.body(body)
		.when()
				.put("/api/v1/admin/products/{id}", id);
	}

	private Response createProduct(String sku, String name, String description, String price) {
		String descriptionJson = description == null ? "null" : "\"" + description + "\"";
		String body = """
				{"sku": "%s", "name": "%s", "description": %s, "price": %s}
				""".formatted(sku, name, descriptionJson, price);

		return authenticated()
				.contentType(ContentType.JSON)
				.body(body)
		.when()
				.post("/api/v1/admin/products");
	}

	private long insertInactiveProduct(String sku) {
		return jdbcTemplate.queryForObject(
				"INSERT INTO products (sku, name, price, active, created_at, updated_at) "
						+ "VALUES (?, ?, ?, false, now(), now()) RETURNING id",
				Long.class, sku, "Inactive product", new BigDecimal("9.99"));
	}
}
