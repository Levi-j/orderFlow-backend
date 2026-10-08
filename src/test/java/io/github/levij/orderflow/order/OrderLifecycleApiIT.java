package io.github.levij.orderflow.order;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
import io.github.levij.orderflow.inventory.InventoryService;
import io.github.levij.orderflow.inventory.MovementReason;
import io.github.levij.orderflow.product.ProductService;
import io.github.levij.orderflow.product.dto.CreateProductRequest;
import io.github.levij.orderflow.product.dto.UpdateProductRequest;
import io.github.levij.orderflow.support.DatabaseCleanup;
import io.github.levij.orderflow.support.TestcontainersConfiguration;
import io.github.levij.orderflow.user.Role;
import io.github.levij.orderflow.user.UserService;
import io.restassured.config.JsonConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.path.json.config.JsonPathConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OrderLifecycleApiIT {

	private static final long UNKNOWN_ID = 999_999L;

	private static final RestAssuredConfig EXACT_DECIMALS = RestAssuredConfig.config()
			.jsonConfig(JsonConfig.jsonConfig().numberReturnType(JsonPathConfig.NumberReturnType.BIG_DECIMAL));

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

	@Autowired
	private JwtTokenService jwtTokenService;

	private long adminId;
	private String adminToken;
	private long customerId;
	private String customerToken;

	@BeforeEach
	void setUp() {
		DatabaseCleanup.deleteAllData(jdbcTemplate);
		userService.ensureBootstrapAdmin("lifecycle.admin@example.com", "lifecycle admin password");
		adminId = userService.findByEmail("lifecycle.admin@example.com").orElseThrow().getId();
		adminToken = token(adminId, Role.ADMIN);
		customerId = registerCustomer("alice@example.com");
		customerToken = token(customerId, Role.CUSTOMER);
	}

	@AfterEach
	void cleanUp() {
		DatabaseCleanup.deleteAllData(jdbcTemplate);
	}

	@Test
	void customerCancelsOwnPendingOrderAndStockIsRestored() {
		long keyboardId = createProduct("KEYBOARD-1", "Keyboard", "49.90");
		long cableId = createProduct("CABLE-1", "Cable", "0.10");
		restock(keyboardId, 10);
		restock(cableId, 5);
		long orderId = placeOrder(customerToken, item(cableId, 3), item(keyboardId, 2));
		assertThat(stock(keyboardId)).isEqualTo(8);
		assertThat(stock(cableId)).isEqualTo(2);

		post(customerToken, "/api/v1/orders/{id}/cancel", orderId).then()
				.statusCode(200)
				.body("id", equalTo((int) orderId))
				.body("customerId", equalTo((int) customerId))
				.body("status", equalTo("CANCELLED"))
				.body("totalAmount", comparesEqualTo(new BigDecimal("100.10")))
				.body("items", hasSize(2))
				.body("items[0].productName", equalTo("Keyboard"))
				.body("items[0].lineTotal", comparesEqualTo(new BigDecimal("99.80")))
				.body("items[1].productName", equalTo("Cable"))
				.body("items[1].lineTotal", comparesEqualTo(new BigDecimal("0.30")));

		assertThat(stock(keyboardId)).isEqualTo(10);
		assertThat(stock(cableId)).isEqualTo(5);
		assertThat(cancellationMovements()).containsExactly(
				new Movement(keyboardId, 2, orderId, customerId),
				new Movement(cableId, 3, orderId, customerId));
		assertThat(storedOrder(orderId)).isEqualTo(new StoredOrder("CANCELLED", 1));
	}

	@Test
	void secondCancellationIsRejectedAndRestocksNothing() {
		long productId = createProduct("TWICE-1", "Cancel twice", "5.00");
		restock(productId, 10);
		long orderId = placeOrder(customerToken, item(productId, 4));

		post(customerToken, "/api/v1/orders/{id}/cancel", orderId).then().statusCode(200);
		assertThat(stock(productId)).isEqualTo(10);

		post(customerToken, "/api/v1/orders/{id}/cancel", orderId).then()
				.statusCode(409)
				.body("code", equalTo("INVALID_STATUS_TRANSITION"))
				.body("detail", equalTo("Only PENDING orders can be cancelled. This order is CANCELLED."));

		assertThat(stock(productId)).isEqualTo(10);
		assertThat(cancellationMovements()).containsExactly(new Movement(productId, 4, orderId, customerId));
		assertThat(storedOrder(orderId)).isEqualTo(new StoredOrder("CANCELLED", 1));
	}

	@Test
	void customerCannotCancelAConfirmedOrder() {
		long productId = createProduct("CONFIRMED-1", "Confirmed product", "5.00");
		restock(productId, 10);
		long orderId = placeOrder(customerToken, item(productId, 3));
		post(adminToken, "/api/v1/admin/orders/{id}/confirm", orderId).then().statusCode(200);

		post(customerToken, "/api/v1/orders/{id}/cancel", orderId).then()
				.statusCode(409)
				.body("code", equalTo("INVALID_STATUS_TRANSITION"));

		assertThat(stock(productId)).isEqualTo(7);
		assertThat(cancellationMovements()).isEmpty();
		assertThat(storedOrder(orderId)).isEqualTo(new StoredOrder("CONFIRMED", 1));
	}

	@Test
	void customerCannotCancelSomeoneElsesOrder() {
		long productId = createProduct("PRIVATE-1", "Private product", "5.00");
		restock(productId, 10);
		long aliceOrderId = placeOrder(customerToken, item(productId, 2));
		String bobToken = token(registerCustomer("bob@example.com"), Role.CUSTOMER);

		post(bobToken, "/api/v1/orders/{id}/cancel", aliceOrderId).then()
				.statusCode(404)
				.body("code", equalTo("RESOURCE_NOT_FOUND"))
				.body("detail", equalTo("Order " + aliceOrderId + " not found"));
		post(bobToken, "/api/v1/orders/{id}/cancel", UNKNOWN_ID).then()
				.statusCode(404)
				.body("code", equalTo("RESOURCE_NOT_FOUND"))
				.body("detail", equalTo("Order " + UNKNOWN_ID + " not found"));

		assertThat(storedOrder(aliceOrderId)).isEqualTo(new StoredOrder("PENDING", 0));
		assertThat(stock(productId)).isEqualTo(8);
		assertThat(cancellationMovements()).isEmpty();
	}

	@Test
	void adminConfirmsPendingOrderWithoutChangingStock() {
		long productId = createProduct("CONFIRM-1", "Confirm product", "5.00");
		restock(productId, 10);
		long orderId = placeOrder(customerToken, item(productId, 3));

		Response confirmed = post(adminToken, "/api/v1/admin/orders/{id}/confirm", orderId);
		confirmed.then()
				.statusCode(200)
				.body("id", equalTo((int) orderId))
				.body("customerId", equalTo((int) customerId))
				.body("status", equalTo("CONFIRMED"))
				.body("items", hasSize(1));
		assertThat(Instant.parse(confirmed.path("updatedAt"))).isAfter(Instant.parse(confirmed.path("createdAt")));

		post(adminToken, "/api/v1/admin/orders/{id}/confirm", orderId).then()
				.statusCode(409)
				.body("code", equalTo("INVALID_STATUS_TRANSITION"))
				.body("detail", equalTo("Only PENDING orders can be confirmed. This order is CONFIRMED."));
		post(adminToken, "/api/v1/admin/orders/{id}/cancel", orderId).then()
				.statusCode(409)
				.body("code", equalTo("INVALID_STATUS_TRANSITION"));

		assertThat(stock(productId)).isEqualTo(7);
		assertThat(cancellationMovements()).isEmpty();
		assertThat(storedOrder(orderId)).isEqualTo(new StoredOrder("CONFIRMED", 1));
	}

	@Test
	void adminCancelsPendingOrderAndIsRecordedAsTheActor() {
		long productId = createProduct("ADMIN-CANCEL-1", "Admin cancel product", "5.00");
		restock(productId, 10);
		long orderId = placeOrder(customerToken, item(productId, 4));

		post(adminToken, "/api/v1/admin/orders/{id}/cancel", orderId).then()
				.statusCode(200)
				.body("status", equalTo("CANCELLED"))
				.body("customerId", equalTo((int) customerId));

		assertThat(stock(productId)).isEqualTo(10);
		assertThat(cancellationMovements()).containsExactly(new Movement(productId, 4, orderId, adminId));

		post(adminToken, "/api/v1/admin/orders/{id}/cancel", orderId).then()
				.statusCode(409)
				.body("code", equalTo("INVALID_STATUS_TRANSITION"));
		post(adminToken, "/api/v1/admin/orders/{id}/confirm", orderId).then()
				.statusCode(409)
				.body("code", equalTo("INVALID_STATUS_TRANSITION"));

		assertThat(stock(productId)).isEqualTo(10);
		assertThat(cancellationMovements()).hasSize(1);
		assertThat(storedOrder(orderId)).isEqualTo(new StoredOrder("CANCELLED", 1));
	}

	@Test
	void cancellationRestoresStockOfAProductDeactivatedAfterOrdering() {
		long productId = createProduct("RETIRED-1", "Retired product", "5.00");
		restock(productId, 10);
		long orderId = placeOrder(customerToken, item(productId, 2));
		productService.updateForAdmin(productId,
				new UpdateProductRequest("Retired product", null, new BigDecimal("5.00"), false));

		post(customerToken, "/api/v1/orders/{id}/cancel", orderId).then()
				.statusCode(200)
				.body("status", equalTo("CANCELLED"));

		assertThat(stock(productId)).isEqualTo(10);
		assertThat(cancellationMovements()).containsExactly(new Movement(productId, 2, orderId, customerId));
		assertThat(productService.getForAdmin(productId).isActive()).isFalse();
	}

	@Test
	void adminListShowsEveryCustomersOrdersNewestFirstAndFiltersByStatus() {
		long productId = createProduct("LIST-1", "Listed product", "1.00");
		restock(productId, 20);
		long bobId = registerCustomer("bob@example.com");
		long first = placeOrder(customerToken, item(productId, 1));
		long second = placeOrder(token(bobId, Role.CUSTOMER), item(productId, 2));
		long third = placeOrder(customerToken, item(productId, 3));
		post(adminToken, "/api/v1/admin/orders/{id}/confirm", first).then().statusCode(200);
		post(adminToken, "/api/v1/admin/orders/{id}/cancel", second).then().statusCode(200);

		get(adminToken, "/api/v1/admin/orders").then()
				.statusCode(200)
				.body("page", equalTo(0))
				.body("size", equalTo(20))
				.body("totalElements", equalTo(3))
				.body("content.id", contains((int) third, (int) second, (int) first))
				.body("content.customerId", contains((int) customerId, (int) bobId, (int) customerId))
				.body("content.status", contains("PENDING", "CANCELLED", "CONFIRMED"))
				.body("content[0].totalAmount", comparesEqualTo(new BigDecimal("3.00")))
				.body("content[0]", not(hasKey("items")));

		assertStatusFilter("PENDING", third);
		assertStatusFilter("CONFIRMED", first);
		assertStatusFilter("CANCELLED", second);

		withToken(adminToken)
				.queryParam("page", 1)
				.queryParam("size", 2)
		.when()
				.get("/api/v1/admin/orders")
		.then()
				.statusCode(200)
				.body("totalPages", equalTo(2))
				.body("content.id", contains((int) first));

		withToken(adminToken)
				.queryParam("status", "SHIPPED")
		.when()
				.get("/api/v1/admin/orders")
		.then()
				.statusCode(400)
				.body("code", equalTo("MALFORMED_REQUEST"));
	}

	@Test
	void adminCanViewAnyCustomersOrderWithItsItems() {
		long productId = createProduct("VIEW-1", "Viewed product", "12.50");
		restock(productId, 5);
		long bobId = registerCustomer("bob@example.com");
		long orderId = placeOrder(token(bobId, Role.CUSTOMER), item(productId, 2));

		get(adminToken, "/api/v1/admin/orders/{id}", orderId).then()
				.statusCode(200)
				.body("id", equalTo((int) orderId))
				.body("customerId", equalTo((int) bobId))
				.body("status", equalTo("PENDING"))
				.body("totalAmount", comparesEqualTo(new BigDecimal("25.00")))
				.body("items[0].productSku", equalTo("VIEW-1"))
				.body("items[0].unitPrice", comparesEqualTo(new BigDecimal("12.50")))
				.body("items[0].quantity", equalTo(2));

		get(adminToken, "/api/v1/admin/orders/{id}", UNKNOWN_ID).then()
				.statusCode(404)
				.body("code", equalTo("RESOURCE_NOT_FOUND"));
		post(adminToken, "/api/v1/admin/orders/{id}/confirm", UNKNOWN_ID).then().statusCode(404);
		post(adminToken, "/api/v1/admin/orders/{id}/cancel", UNKNOWN_ID).then().statusCode(404);
	}

	@Test
	void lifecycleRoutesAreRestrictedByRole() {
		long productId = createProduct("ROLES-1", "Roles product", "1.00");
		restock(productId, 10);
		long orderId = placeOrder(customerToken, item(productId, 1));

		given().port(port).when().post("/api/v1/orders/{id}/cancel", orderId).then()
				.statusCode(401).body("code", equalTo("UNAUTHENTICATED"));
		given().port(port).when().get("/api/v1/admin/orders").then()
				.statusCode(401).body("code", equalTo("UNAUTHENTICATED"));
		given().port(port).when().post("/api/v1/admin/orders/{id}/confirm", orderId).then()
				.statusCode(401).body("code", equalTo("UNAUTHENTICATED"));

		get(customerToken, "/api/v1/admin/orders").then().statusCode(403).body("code", equalTo("ACCESS_DENIED"));
		get(customerToken, "/api/v1/admin/orders/{id}", orderId).then().statusCode(403);
		post(customerToken, "/api/v1/admin/orders/{id}/confirm", orderId).then().statusCode(403);
		post(customerToken, "/api/v1/admin/orders/{id}/cancel", orderId).then().statusCode(403);

		post(adminToken, "/api/v1/orders/{id}/cancel", orderId).then()
				.statusCode(403).body("code", equalTo("ACCESS_DENIED"));

		assertThat(storedOrder(orderId)).isEqualTo(new StoredOrder("PENDING", 0));
		assertThat(stock(productId)).isEqualTo(9);
		assertThat(cancellationMovements()).isEmpty();
	}

	private void assertStatusFilter(String status, long expectedOrderId) {
		withToken(adminToken)
				.queryParam("status", status)
		.when()
				.get("/api/v1/admin/orders")
		.then()
				.statusCode(200)
				.body("totalElements", equalTo(1))
				.body("content.id", contains((int) expectedOrderId))
				.body("content[0].status", equalTo(status));
	}

	private long registerCustomer(String email) {
		return userService.registerCustomer(email, "lifecycle customer password").getId();
	}

	private String token(long userId, Role role) {
		return jwtTokenService.issue(userId, role).value();
	}

	private long createProduct(String sku, String name, String price) {
		return productService.create(new CreateProductRequest(sku, name, null, new BigDecimal(price))).getId();
	}

	private void restock(long productId, int quantity) {
		inventoryService.adjust(productId, quantity, MovementReason.RESTOCK, null, adminId);
	}

	@SafeVarargs
	private long placeOrder(String token, Map<String, Object>... items) {
		return withToken(token)
				.contentType(ContentType.JSON)
				.body(Map.of("items", List.of(items)))
		.when()
				.post("/api/v1/orders")
		.then()
				.statusCode(201)
				.extract().jsonPath().getLong("id");
	}

	private static Map<String, Object> item(long productId, int quantity) {
		return Map.of("productId", productId, "quantity", quantity);
	}

	private RequestSpecification withToken(String token) {
		return given().port(port).config(EXACT_DECIMALS).auth().oauth2(token);
	}

	private Response get(String token, String path, Object... pathParams) {
		return withToken(token).when().get(path, pathParams);
	}

	private Response post(String token, String path, Object... pathParams) {
		return withToken(token).when().post(path, pathParams);
	}

	private int stock(long productId) {
		return jdbcTemplate.queryForObject(
				"SELECT quantity_on_hand FROM inventory_items WHERE product_id = ?", Integer.class, productId);
	}

	private List<Movement> cancellationMovements() {
		return jdbcTemplate.query(
				"SELECT product_id, quantity_change, order_id, performed_by_user_id FROM inventory_movements "
						+ "WHERE reason = 'ORDER_CANCELLED' ORDER BY id",
				(row, rowNumber) -> new Movement(row.getLong("product_id"), row.getInt("quantity_change"),
						row.getLong("order_id"), row.getLong("performed_by_user_id")));
	}

	private StoredOrder storedOrder(long orderId) {
		return jdbcTemplate.queryForObject("SELECT status, version FROM orders WHERE id = ?",
				(row, rowNumber) -> new StoredOrder(row.getString("status"), row.getLong("version")), orderId);
	}

	private record Movement(long productId, int quantityChange, long orderId, long performedByUserId) {
	}

	private record StoredOrder(String status, long version) {
	}
}
