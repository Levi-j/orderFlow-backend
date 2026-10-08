package io.github.levij.orderflow.inventory;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.levij.orderflow.auth.JwtTokenService;
import io.github.levij.orderflow.product.ProductService;
import io.github.levij.orderflow.product.dto.CreateProductRequest;
import io.github.levij.orderflow.support.DatabaseCleanup;
import io.github.levij.orderflow.support.TestcontainersConfiguration;
import io.github.levij.orderflow.user.Role;
import io.github.levij.orderflow.user.UserService;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class InventoryApiIT {

	private static final long UNKNOWN_PRODUCT_ID = 999_999L;

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private UserService userService;

	@Autowired
	private ProductService productService;

	@Autowired
	private JwtTokenService jwtTokenService;

	private long adminId;
	private String adminToken;
	private long productId;

	@BeforeEach
	void setUp() {
		DatabaseCleanup.deleteInventoryProductsAndUsers(jdbcTemplate);
		userService.ensureBootstrapAdmin("inventory.admin@example.com", "inventory admin password");
		adminId = userService.findByEmail("inventory.admin@example.com").orElseThrow().getId();
		adminToken = jwtTokenService.issue(adminId, Role.ADMIN).value();
		productId = productService.create(
				new CreateProductRequest("INV-1", "Inventory widget", null, new BigDecimal("2.50"))).getId();
	}

	@AfterEach
	void cleanUp() {
		DatabaseCleanup.deleteInventoryProductsAndUsers(jdbcTemplate);
	}

	@Test
	void productWithoutInventoryHasZeroStockAndReadingWritesNothing() {
		getStock(productId).then()
				.statusCode(200)
				.body("productId", equalTo((int) productId))
				.body("quantityOnHand", equalTo(0));

		assertThat(count("inventory_items")).isZero();
		assertThat(count("inventory_movements")).isZero();
	}

	@Test
	void restockAndAdjustmentChangeStockAndAreRecordedNewestFirst() {
		adjust(productId, 10, "RESTOCK", "Initial stock").then()
				.statusCode(200)
				.body("quantityOnHand", equalTo(10));
		adjust(productId, -4, "ADJUSTMENT", "Damaged in storage").then()
				.statusCode(200)
				.body("quantityOnHand", equalTo(6));

		getStock(productId).then().statusCode(200).body("quantityOnHand", equalTo(6));

		movements(productId).then()
				.statusCode(200)
				.body("page", equalTo(0))
				.body("size", equalTo(20))
				.body("totalElements", equalTo(2))
				.body("totalPages", equalTo(1))
				.body("content[0].quantityChange", equalTo(-4))
				.body("content[0].reason", equalTo("ADJUSTMENT"))
				.body("content[0].note", equalTo("Damaged in storage"))
				.body("content[1].quantityChange", equalTo(10))
				.body("content[1].reason", equalTo("RESTOCK"))
				.body("content[0].performedByUserId", equalTo((int) adminId))
				.body("content[1].performedByUserId", equalTo((int) adminId))
				.body("content[0].orderId", nullValue())
				.body("content[0].productId", equalTo((int) productId))
				.body("content[0].id", notNullValue())
				.body("content[0].createdAt", notNullValue());
	}

	@Test
	void overWithdrawalIsRejectedAndChangesNothing() {
		adjust(productId, 6, "RESTOCK", null).then().statusCode(200);

		String body = adjust(productId, -7, "ADJUSTMENT", null).then()
				.statusCode(409)
				.contentType(containsString("application/problem+json"))
				.body("code", equalTo("INSUFFICIENT_STOCK"))
				.extract().asString();

		assertThat(body).doesNotContain("SQL", "ck_inventory", "quantity_on_hand", "Exception");
		getStock(productId).then().body("quantityOnHand", equalTo(6));
		assertThat(count("inventory_movements")).isEqualTo(1);
	}

	@Test
	void failedFirstAdjustmentDoesNotLeaveAStockRowBehind() {
		adjust(productId, -1, "ADJUSTMENT", null).then()
				.statusCode(409)
				.body("code", equalTo("INSUFFICIENT_STOCK"));

		assertThat(count("inventory_items")).isZero();
		assertThat(count("inventory_movements")).isZero();
	}

	@Test
	void zeroChangeAndNonPositiveRestockAreInvalid() {
		adjust(productId, 0, "ADJUSTMENT", null).then()
				.statusCode(400)
				.body("code", equalTo("VALIDATION_FAILED"))
				.body("errors.find { it.field == 'quantityChange' }.message", equalTo("quantityChange must not be zero"));
		adjust(productId, -3, "RESTOCK", null).then()
				.statusCode(400)
				.body("code", equalTo("VALIDATION_FAILED"))
				.body("errors.find { it.field == 'quantityChange' }.message",
						equalTo("quantityChange must be positive for RESTOCK"));

		assertThat(count("inventory_items")).isZero();
		assertThat(count("inventory_movements")).isZero();
	}

	@Test
	void orderReasonsCannotBeSubmittedByClients() {
		for (String systemReason : new String[] { "ORDER_PLACED", "ORDER_CANCELLED" }) {
			adjust(productId, 1, systemReason, null).then().statusCode(400);
		}

		assertThat(count("inventory_items")).isZero();
		assertThat(count("inventory_movements")).isZero();
	}

	@Test
	void unknownProductReturns404Everywhere() {
		getStock(UNKNOWN_PRODUCT_ID).then().statusCode(404).body("code", equalTo("RESOURCE_NOT_FOUND"));
		adjust(UNKNOWN_PRODUCT_ID, 5, "RESTOCK", null).then().statusCode(404).body("code", equalTo("RESOURCE_NOT_FOUND"));
		movements(UNKNOWN_PRODUCT_ID).then().statusCode(404).body("code", equalTo("RESOURCE_NOT_FOUND"));

		assertThat(count("inventory_items")).isZero();
	}

	@Test
	void customersAreForbiddenAndAnonymousCallersUnauthenticated() {
		long customerId = userService.registerCustomer("inventory.customer@example.com", "inventory customer password")
				.getId();
		String customerToken = jwtTokenService.issue(customerId, Role.CUSTOMER).value();

		given().port(port)
				.auth().oauth2(customerToken)
		.when()
				.get("/api/v1/admin/inventory/{productId}", productId)
		.then()
				.statusCode(403)
				.body("code", equalTo("ACCESS_DENIED"));

		given().port(port)
		.when()
				.get("/api/v1/admin/inventory/{productId}", productId)
		.then()
				.statusCode(401)
				.body("code", equalTo("UNAUTHENTICATED"));
	}

	@Test
	void historyIsPaginated() {
		adjust(productId, 1, "RESTOCK", null);
		adjust(productId, 2, "RESTOCK", null);
		adjust(productId, 3, "RESTOCK", null);

		given().port(port)
				.auth().oauth2(adminToken)
				.queryParam("size", 2)
		.when()
				.get("/api/v1/admin/inventory/{productId}/movements", productId)
		.then()
				.statusCode(200)
				.body("size", equalTo(2))
				.body("totalElements", equalTo(3))
				.body("totalPages", equalTo(2))
				.body("content.quantityChange", hasItem(3));
	}

	private Response getStock(long productId) {
		return given().port(port)
				.auth().oauth2(adminToken)
		.when()
				.get("/api/v1/admin/inventory/{productId}", productId);
	}

	private Response adjust(long productId, int quantityChange, String reason, String note) {
		Map<String, Object> body = new HashMap<>();
		body.put("quantityChange", quantityChange);
		body.put("reason", reason);
		body.put("note", note);
		return given().port(port)
				.auth().oauth2(adminToken)
				.contentType(ContentType.JSON)
				.body(body)
		.when()
				.post("/api/v1/admin/inventory/{productId}/adjustments", productId);
	}

	private Response movements(long productId) {
		return given().port(port)
				.auth().oauth2(adminToken)
		.when()
				.get("/api/v1/admin/inventory/{productId}/movements", productId);
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}
}
