package io.github.levij.orderflow.order;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

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
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OrderApiIT {

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
	private long customerId;
	private String customerToken;

	@BeforeEach
	void setUp() {
		DatabaseCleanup.deleteAllData(jdbcTemplate);
		userService.ensureBootstrapAdmin("orders.admin@example.com", "orders admin password");
		adminId = userService.findByEmail("orders.admin@example.com").orElseThrow().getId();
		customerId = registerCustomer("alice@example.com");
		customerToken = token(customerId, Role.CUSTOMER);
	}

	@AfterEach
	void cleanUp() {
		DatabaseCleanup.deleteAllData(jdbcTemplate);
	}

	@Test
	void customerPlacesAMultiItemOrderAndStockIsReduced() {
		long keyboardId = createProduct("KEYBOARD-1", "Keyboard", "49.90");
		long cableId = createProduct("CABLE-1", "Cable", "0.10");
		restock(keyboardId, 10);
		restock(cableId, 5);

		Response response = placeOrder(customerToken, order(item(cableId, 3), item(keyboardId, 2)));

		long orderId = response.then()
				.statusCode(201)
				.body("customerId", equalTo((int) customerId))
				.body("status", equalTo("PENDING"))
				.body("totalAmount", comparesEqualTo(new BigDecimal("100.10")))
				.body("createdAt", notNullValue())
				.body("updatedAt", notNullValue())
				.body("items", hasSize(2))
				.body("items[0].productId", equalTo((int) keyboardId))
				.body("items[0].productSku", equalTo("KEYBOARD-1"))
				.body("items[0].productName", equalTo("Keyboard"))
				.body("items[0].unitPrice", comparesEqualTo(new BigDecimal("49.90")))
				.body("items[0].quantity", equalTo(2))
				.body("items[0].lineTotal", comparesEqualTo(new BigDecimal("99.80")))
				.body("items[1].productId", equalTo((int) cableId))
				.body("items[1].productSku", equalTo("CABLE-1"))
				.body("items[1].productName", equalTo("Cable"))
				.body("items[1].unitPrice", comparesEqualTo(new BigDecimal("0.10")))
				.body("items[1].quantity", equalTo(3))
				.body("items[1].lineTotal", comparesEqualTo(new BigDecimal("0.30")))
				.extract().jsonPath().getLong("id");

		String location = response.header("Location");
		assertThat(location).endsWith("/api/v1/orders/" + orderId);
		withToken(customerToken).get(location).then()
				.statusCode(200)
				.body("id", equalTo((int) orderId))
				.body("totalAmount", comparesEqualTo(new BigDecimal("100.10")))
				.body("items", hasSize(2));

		assertThat(stock(keyboardId)).isEqualTo(8);
		assertThat(stock(cableId)).isEqualTo(2);
		assertThat(orderPlacedMovements()).containsExactly(
				new OrderMovement(keyboardId, -2, orderId, customerId),
				new OrderMovement(cableId, -3, orderId, customerId));
	}

	@Test
	void pricesAndCustomerComeFromTheServerNotFromTheRequest() {
		long keyboardId = createProduct("KEYBOARD-1", "Keyboard", "49.90");
		restock(keyboardId, 5);
		long otherCustomerId = registerCustomer("bob@example.com");
		Map<String, Object> tamperedItem = Map.of("productId", keyboardId, "quantity", 2,
				"unitPrice", 0.01, "lineTotal", 0.02, "productName", "Free keyboard");
		Map<String, Object> tamperedOrder = Map.of("items", List.of(tamperedItem),
				"customerId", otherCustomerId, "status", "CONFIRMED", "totalAmount", 0.02);

		placeOrder(customerToken, tamperedOrder).then()
				.statusCode(201)
				.body("customerId", equalTo((int) customerId))
				.body("status", equalTo("PENDING"))
				.body("items[0].productName", equalTo("Keyboard"))
				.body("items[0].unitPrice", comparesEqualTo(new BigDecimal("49.90")))
				.body("items[0].lineTotal", comparesEqualTo(new BigDecimal("99.80")))
				.body("totalAmount", comparesEqualTo(new BigDecimal("99.80")));

		Map<String, Object> stored = jdbcTemplate.queryForMap("SELECT customer_id, total_amount FROM orders");
		assertThat(stored.get("customer_id")).isEqualTo(customerId);
		assertThat((BigDecimal) stored.get("total_amount")).isEqualByComparingTo("99.80");
	}

	@Test
	void insufficientStockForOneItemRollsBackTheWholeOrder() {
		long firstId = createProduct("FIRST-1", "First product", "10.00");
		long secondId = createProduct("SECOND-1", "Second product", "20.00");
		restock(firstId, 10);
		restock(secondId, 1);
		long orderIdsUsedBefore = identityValuesUsed("orders");
		long movementIdsUsedBefore = identityValuesUsed("inventory_movements");

		placeOrder(customerToken, order(item(secondId, 2), item(firstId, 4))).then()
				.statusCode(409)
				.body("code", equalTo("INSUFFICIENT_STOCK"))
				.body("detail", equalTo("Insufficient stock for product " + secondId + "."));

		assertThat(stock(firstId)).isEqualTo(10);
		assertThat(stock(secondId)).isEqualTo(1);
		assertThat(count("orders")).isZero();
		assertThat(count("order_items")).isZero();
		assertThat(orderPlacedMovements()).isEmpty();
		assertThat(count("inventory_movements")).as("only the two RESTOCK movements remain").isEqualTo(2);

		assertThat(identityValuesUsed("orders")).isEqualTo(orderIdsUsedBefore + 1);
		assertThat(identityValuesUsed("inventory_movements")).isEqualTo(movementIdsUsedBefore + 1);
	}

	@Test
	void inactiveAndUnknownProductsCannotBeOrdered() {
		long activeId = createProduct("ACTIVE-1", "Active product", "5.00");
		long inactiveId = createProduct("INACTIVE-1", "Inactive product", "5.00");
		restock(activeId, 10);
		restock(inactiveId, 10);
		productService.updateForAdmin(inactiveId,
				new UpdateProductRequest("Inactive product", null, new BigDecimal("5.00"), false));

		placeOrder(customerToken, order(item(activeId, 1), item(inactiveId, 1))).then()
				.statusCode(409)
				.body("code", equalTo("PRODUCT_NOT_AVAILABLE"))
				.body("detail", equalTo("Product " + inactiveId + " is not available for ordering."));
		placeOrder(customerToken, order(item(activeId, 1), item(UNKNOWN_ID, 1))).then()
				.statusCode(409)
				.body("code", equalTo("PRODUCT_NOT_AVAILABLE"))
				.body("detail", equalTo("Product " + UNKNOWN_ID + " is not available for ordering."));

		assertThat(count("orders")).isZero();
		assertThat(count("order_items")).isZero();
		assertThat(stock(activeId)).isEqualTo(10);
		assertThat(stock(inactiveId)).isEqualTo(10);
		assertThat(orderPlacedMovements()).isEmpty();
	}

	@Test
	void customersCanOnlySeeTheirOwnOrders() {
		long productId = createProduct("SHARED-1", "Shared product", "3.00");
		restock(productId, 10);
		long aliceOrderId = placedOrderId(customerToken, order(item(productId, 1)));
		String bobToken = token(registerCustomer("bob@example.com"), Role.CUSTOMER);
		long bobOrderId = placedOrderId(bobToken, order(item(productId, 2)));

		getOrder(customerToken, aliceOrderId).then()
				.statusCode(200)
				.body("id", equalTo((int) aliceOrderId))
				.body("customerId", equalTo((int) customerId));

		getOrder(bobToken, aliceOrderId).then()
				.statusCode(404)
				.body("code", equalTo("RESOURCE_NOT_FOUND"))
				.body("detail", equalTo("Order " + aliceOrderId + " not found"));
		getOrder(bobToken, UNKNOWN_ID).then()
				.statusCode(404)
				.body("code", equalTo("RESOURCE_NOT_FOUND"))
				.body("detail", equalTo("Order " + UNKNOWN_ID + " not found"));

		listOrders(bobToken).then()
				.statusCode(200)
				.body("totalElements", equalTo(1))
				.body("content.id", contains((int) bobOrderId));
		listOrders(customerToken).then()
				.statusCode(200)
				.body("totalElements", equalTo(1))
				.body("content.id", contains((int) aliceOrderId));
	}

	@Test
	void orderKeepsItsSnapshotAfterTheProductChanges() {
		long keyboardId = createProduct("KEYBOARD-1", "Keyboard", "49.90");
		restock(keyboardId, 5);
		long orderId = placedOrderId(customerToken, order(item(keyboardId, 2)));

		productService.updateForAdmin(keyboardId,
				new UpdateProductRequest("Keyboard Pro", null, new BigDecimal("59.90"), true));

		getOrder(customerToken, orderId).then()
				.statusCode(200)
				.body("totalAmount", comparesEqualTo(new BigDecimal("99.80")))
				.body("items[0].productSku", equalTo("KEYBOARD-1"))
				.body("items[0].productName", equalTo("Keyboard"))
				.body("items[0].unitPrice", comparesEqualTo(new BigDecimal("49.90")))
				.body("items[0].lineTotal", comparesEqualTo(new BigDecimal("99.80")));

		given().port(port).config(EXACT_DECIMALS)
		.when()
				.get("/api/v1/products/{id}", keyboardId)
		.then()
				.statusCode(200)
				.body("name", equalTo("Keyboard Pro"))
				.body("price", comparesEqualTo(new BigDecimal("59.90")));
	}

	@Test
	void invalidOrdersAreRejectedBeforeAnythingIsSaved() {
		long productId = createProduct("VALID-1", "Valid product", "1.00");
		restock(productId, 10);
		List<Map<String, Object>> fiftyOneItems = LongStream.rangeClosed(1, 51).mapToObj(id -> item(id, 1)).toList();

		assertValidationError(Map.of("items", List.of()), "items");
		assertValidationError(Map.of("items", fiftyOneItems), "items");
		assertValidationError(order(item(productId, 0)), "items[0].quantity");
		assertValidationError(order(item(productId, 1001)), "items[0].quantity");
		assertValidationError(order(item(productId, 1), item(productId, 2)), "items")
				.body("errors.find { it.field == 'items' }.message",
						equalTo("must not contain the same product more than once"));

		assertThat(count("orders")).isZero();
		assertThat(stock(productId)).isEqualTo(10);
		assertThat(orderPlacedMovements()).isEmpty();
	}

	@Test
	void orderListIsPagedNewestFirst() {
		long productId = createProduct("PAGED-1", "Paged product", "1.00");
		restock(productId, 10);
		long first = placedOrderId(customerToken, order(item(productId, 1)));
		long second = placedOrderId(customerToken, order(item(productId, 2)));
		long third = placedOrderId(customerToken, order(item(productId, 3)));

		listOrders(customerToken).then()
				.statusCode(200)
				.body("page", equalTo(0))
				.body("size", equalTo(20))
				.body("totalElements", equalTo(3))
				.body("totalPages", equalTo(1))
				.body("content.id", contains((int) third, (int) second, (int) first))
				.body("content[0].status", equalTo("PENDING"))
				.body("content[0].totalAmount", comparesEqualTo(new BigDecimal("3.00")))
				.body("content[0].createdAt", notNullValue())
				.body("content[0]", not(hasKey("items")));

		withToken(customerToken)
				.queryParam("page", 1)
				.queryParam("size", 2)
		.when()
				.get("/api/v1/orders")
		.then()
				.statusCode(200)
				.body("page", equalTo(1))
				.body("size", equalTo(2))
				.body("totalElements", equalTo(3))
				.body("totalPages", equalTo(2))
				.body("content.id", contains((int) first));
	}

	@Test
	void onlyCustomersCanUseTheOrderRoutes() {
		long productId = createProduct("SECURE-1", "Secure product", "1.00");
		restock(productId, 10);
		long orderId = placedOrderId(customerToken, order(item(productId, 1)));
		String adminToken = token(adminId, Role.ADMIN);

		given().port(port)
		.when()
				.get("/api/v1/orders")
		.then()
				.statusCode(401)
				.body("code", equalTo("UNAUTHENTICATED"));
		given().port(port)
				.contentType(ContentType.JSON)
				.body(order(item(productId, 1)))
		.when()
				.post("/api/v1/orders")
		.then()
				.statusCode(401)
				.body("code", equalTo("UNAUTHENTICATED"));

		listOrders(adminToken).then().statusCode(403).body("code", equalTo("ACCESS_DENIED"));
		getOrder(adminToken, orderId).then().statusCode(403).body("code", equalTo("ACCESS_DENIED"));
		placeOrder(adminToken, order(item(productId, 1))).then().statusCode(403).body("code", equalTo("ACCESS_DENIED"));

		assertThat(count("orders")).isEqualTo(1);
		assertThat(stock(productId)).isEqualTo(9);
	}

	private ValidatableResponse assertValidationError(Object body, String field) {
		return placeOrder(customerToken, body).then()
				.statusCode(400)
				.body("code", equalTo("VALIDATION_FAILED"))
				.body("errors.field", hasItem(field));
	}

	private long registerCustomer(String email) {
		return userService.registerCustomer(email, "order customer password").getId();
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

	private RequestSpecification withToken(String token) {
		return given().port(port).config(EXACT_DECIMALS).auth().oauth2(token);
	}

	private Response placeOrder(String token, Object body) {
		return withToken(token)
				.contentType(ContentType.JSON)
				.body(body)
		.when()
				.post("/api/v1/orders");
	}

	private long placedOrderId(String token, Object body) {
		return placeOrder(token, body).then().statusCode(201).extract().jsonPath().getLong("id");
	}

	private Response getOrder(String token, long orderId) {
		return withToken(token).when().get("/api/v1/orders/{id}", orderId);
	}

	private Response listOrders(String token) {
		return withToken(token).when().get("/api/v1/orders");
	}

	private static Map<String, Object> item(long productId, int quantity) {
		return Map.of("productId", productId, "quantity", quantity);
	}

	@SafeVarargs
	private static Map<String, Object> order(Map<String, Object>... items) {
		return Map.of("items", List.of(items));
	}

	private int stock(long productId) {
		return jdbcTemplate.queryForObject(
				"SELECT quantity_on_hand FROM inventory_items WHERE product_id = ?", Integer.class, productId);
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

	private List<OrderMovement> orderPlacedMovements() {
		return jdbcTemplate.query(
				"SELECT product_id, quantity_change, order_id, performed_by_user_id FROM inventory_movements "
						+ "WHERE reason = 'ORDER_PLACED' ORDER BY product_id",
				(row, rowNumber) -> new OrderMovement(row.getLong("product_id"), row.getInt("quantity_change"),
						row.getLong("order_id"), row.getLong("performed_by_user_id")));
	}

	private long identityValuesUsed(String table) {
		Long lastValue = jdbcTemplate.queryForObject(
				"SELECT pg_sequence_last_value(pg_get_serial_sequence(?, 'id')::regclass)", Long.class, table);
		return lastValue == null ? 0 : lastValue;
	}

	private record OrderMovement(long productId, int quantityChange, long orderId, long performedByUserId) {
	}
}
