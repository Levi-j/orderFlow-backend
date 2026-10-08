package io.github.levij.orderflow.order;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.math.BigDecimal;
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
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OrderIdempotencyApiIT {

	private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
	private static final String REPLAYED = "Idempotent-Replayed";

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
	private long keyboardId;
	private long cableId;

	@BeforeEach
	void setUp() {
		DatabaseCleanup.deleteAllData(jdbcTemplate);
		userService.ensureBootstrapAdmin("retry.admin@example.com", "retry admin password");
		adminId = userService.findByEmail("retry.admin@example.com").orElseThrow().getId();
		customerId = registerCustomer("alice@example.com");
		customerToken = token(customerId, Role.CUSTOMER);
		keyboardId = createProduct("KEYBOARD-1", "Keyboard", "49.90");
		cableId = createProduct("CABLE-1", "Cable", "0.10");
		restock(keyboardId, 10);
		restock(cableId, 5);
	}

	@AfterEach
	void cleanUp() {
		DatabaseCleanup.deleteAllData(jdbcTemplate);
	}

	@Test
	void missingIdempotencyKeyIsRejected() {
		withToken(customerToken)
				.contentType(ContentType.JSON)
				.body(order(item(keyboardId, 1)))
		.when()
				.post("/api/v1/orders")
		.then()
				.statusCode(400)
				.body("code", equalTo("MALFORMED_REQUEST"))
				.body("detail", containsString(IDEMPOTENCY_KEY));

		assertNothingWasPlaced();
	}

	@Test
	void malformedIdempotencyKeysAreRejectedButOneHundredCharactersAreAllowed() {
		for (String key : List.of("", "has space", "semi;colon", "dot.ted", "a".repeat(101))) {
			place(customerToken, key, order(item(keyboardId, 1))).then()
					.statusCode(400)
					.body("code", equalTo("MALFORMED_REQUEST"))
					.body("detail", containsString(IDEMPOTENCY_KEY));
		}
		place(customerToken, "dot.ted", order(item(keyboardId, 1))).then()
				.body("detail", equalTo("The Idempotency-Key header must be 1 to 100 characters long and contain "
						+ "only letters, digits, '_' or '-'."));

		assertNothingWasPlaced();

		place(customerToken, "a".repeat(100), order(item(keyboardId, 1))).then().statusCode(201);
	}

	@Test
	void repeatingTheSameRequestReturnsTheOriginalOrder() {
		Response first = place(customerToken, "checkout-1", order(item(cableId, 3), item(keyboardId, 2)));
		first.then()
				.statusCode(201)
				.header(REPLAYED, nullValue());
		long orderId = first.jsonPath().getLong("id");

		Response replay = place(customerToken, "checkout-1", order(item(cableId, 3), item(keyboardId, 2)));

		replay.then()
				.statusCode(201)
				.header(REPLAYED, equalTo("true"))
				.header("Location", equalTo(first.header("Location")));
		assertThat(replay.jsonPath().getLong("id")).isEqualTo(orderId);
		assertThat(replay.asString()).isEqualTo(first.asString());

		assertThat(count("orders")).isEqualTo(1);
		assertThat(stock(keyboardId)).isEqualTo(8);
		assertThat(stock(cableId)).isEqualTo(2);
		assertThat(movementCount("ORDER_PLACED")).isEqualTo(2);
	}

	@Test
	void sameItemsInADifferentOrderAreTheSameRequest() {
		long orderId = placedOrderId(customerToken, "checkout-2", order(item(keyboardId, 2), item(cableId, 3)));

		place(customerToken, "checkout-2", order(item(cableId, 3), item(keyboardId, 2))).then()
				.statusCode(201)
				.header(REPLAYED, equalTo("true"))
				.body("id", equalTo((int) orderId));

		assertThat(count("orders")).isEqualTo(1);
		assertThat(stock(keyboardId)).isEqualTo(8);
	}

	@Test
	void reusingAKeyForADifferentRequestIsRejectedWithoutSideEffects() {
		placedOrderId(customerToken, "checkout-3", order(item(keyboardId, 2)));
		String storedHash = jdbcTemplate.queryForObject("SELECT request_hash FROM orders", String.class);

		for (Map<String, Object> differentRequest : List.of(
				order(item(keyboardId, 3)),
				order(item(cableId, 2)),
				order(item(keyboardId, 2), item(cableId, 1)))) {
			String body = place(customerToken, "checkout-3", differentRequest).then()
					.statusCode(409)
					.body("code", equalTo("IDEMPOTENCY_KEY_REUSED"))
					.body("detail", equalTo("The idempotency key was already used for a different order request."))
					.extract().asString();
			assertThat(body).doesNotContain(storedHash);
		}

		assertThat(count("orders")).isEqualTo(1);
		assertThat(stock(keyboardId)).isEqualTo(8);
		assertThat(stock(cableId)).isEqualTo(5);
		assertThat(movementCount("ORDER_PLACED")).isEqualTo(1);
	}

	@Test
	void theSameKeyFromAnotherCustomerPlacesASeparateOrder() {
		long aliceOrderId = placedOrderId(customerToken, "shared-key", order(item(keyboardId, 1)));
		long bobId = registerCustomer("bob@example.com");

		Response bobOrder = place(token(bobId, Role.CUSTOMER), "shared-key", order(item(keyboardId, 1)));

		bobOrder.then()
				.statusCode(201)
				.header(REPLAYED, nullValue())
				.body("customerId", equalTo((int) bobId));
		assertThat(bobOrder.jsonPath().getLong("id")).isNotEqualTo(aliceOrderId);
		assertThat(count("orders")).isEqualTo(2);
		assertThat(stock(keyboardId)).isEqualTo(8);
	}

	@Test
	void keysAreCaseSensitive() {
		long lowerCaseOrderId = placedOrderId(customerToken, "order-1", order(item(keyboardId, 1)));

		Response upperCase = place(customerToken, "ORDER-1", order(item(keyboardId, 1)));

		upperCase.then()
				.statusCode(201)
				.header(REPLAYED, nullValue());
		assertThat(upperCase.jsonPath().getLong("id")).isNotEqualTo(lowerCaseOrderId);
		assertThat(count("orders")).isEqualTo(2);
	}

	@Test
	void replayDoesNotDependOnCurrentStockOrProductAvailability() {
		long orderId = placedOrderId(customerToken, "last-keyboards", order(item(keyboardId, 10)));
		productService.updateForAdmin(keyboardId,
				new UpdateProductRequest("Keyboard", null, new BigDecimal("49.90"), false));

		place(customerToken, "last-keyboards", order(item(keyboardId, 10))).then()
				.statusCode(201)
				.header(REPLAYED, equalTo("true"))
				.body("id", equalTo((int) orderId));

		assertThat(stock(keyboardId)).isZero();
		assertThat(count("orders")).isEqualTo(1);
		assertThat(movementCount("ORDER_PLACED")).isEqualTo(1);
	}

	@Test
	void replayRepeatsThePlacementResponseEvenAfterTheOrderWasCancelled() {
		Response first = place(customerToken, "checkout-cancel", order(item(keyboardId, 2)));
		first.then().statusCode(201);
		long orderId = first.jsonPath().getLong("id");
		withToken(token(adminId, Role.ADMIN)).post("/api/v1/admin/orders/{id}/cancel", orderId).then().statusCode(200);

		Response replay = place(customerToken, "checkout-cancel", order(item(keyboardId, 2)));

		replay.then()
				.statusCode(201)
				.header(REPLAYED, equalTo("true"))
				.body("status", equalTo("PENDING"));
		assertThat(replay.asString()).isEqualTo(first.asString());
		withToken(customerToken).get(replay.header("Location")).then()
				.statusCode(200)
				.body("status", equalTo("CANCELLED"));

		assertThat(stock(keyboardId)).isEqualTo(10);
		assertThat(movementCount("ORDER_PLACED")).isEqualTo(1);
		assertThat(movementCount("ORDER_CANCELLED")).isEqualTo(1);
	}

	@Test
	void failedPlacementDoesNotUseUpTheKey() {
		place(customerToken, "retry-after-failure", order(item(cableId, 6))).then()
				.statusCode(409)
				.body("code", equalTo("INSUFFICIENT_STOCK"));
		assertThat(count("orders")).isZero();
		restock(cableId, 5);

		place(customerToken, "retry-after-failure", order(item(cableId, 6))).then()
				.statusCode(201)
				.header(REPLAYED, nullValue());

		assertThat(count("orders")).isEqualTo(1);
		assertThat(stock(cableId)).isEqualTo(4);
	}

	private void assertNothingWasPlaced() {
		assertThat(count("orders")).isZero();
		assertThat(stock(keyboardId)).isEqualTo(10);
		assertThat(movementCount("ORDER_PLACED")).isZero();
	}

	private long registerCustomer(String email) {
		return userService.registerCustomer(email, "retry customer password").getId();
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
		return given().port(port).auth().oauth2(token);
	}

	private Response place(String token, String idempotencyKey, Object body) {
		return withToken(token)
				.header(IDEMPOTENCY_KEY, idempotencyKey)
				.contentType(ContentType.JSON)
				.body(body)
		.when()
				.post("/api/v1/orders");
	}

	private long placedOrderId(String token, String idempotencyKey, Object body) {
		return place(token, idempotencyKey, body).then().statusCode(201).extract().jsonPath().getLong("id");
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

	private int movementCount(String reason) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_movements WHERE reason = ?",
				Integer.class, reason);
	}
}
