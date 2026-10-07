package io.github.levij.orderflow.product;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.levij.orderflow.support.TestcontainersConfiguration;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ProductApiIT {

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanProducts() {
		jdbcTemplate.update("DELETE FROM products");
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

		given()
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

		given().port(port)
		.when()
				.get("/api/v1/admin/products/{id}", inactiveId)
		.then()
				.statusCode(200)
				.body("sku", equalTo("INACTIVE-1"))
				.body("active", equalTo(false));

		given().port(port)
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

		given().port(port)
		.when()
				.get("/api/v1/admin/products/{id}", 999999)
		.then()
				.statusCode(404);
	}

	private Response createProduct(String sku, String name, String description, String price) {
		String descriptionJson = description == null ? "null" : "\"" + description + "\"";
		String body = """
				{"sku": "%s", "name": "%s", "description": %s, "price": %s}
				""".formatted(sku, name, descriptionJson, price);

		return given().port(port)
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
