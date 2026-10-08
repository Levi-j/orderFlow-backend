package io.github.levij.orderflow.order;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
import io.github.levij.orderflow.support.DatabaseCleanup;
import io.github.levij.orderflow.support.TestcontainersConfiguration;
import io.github.levij.orderflow.user.Role;
import io.github.levij.orderflow.user.UserService;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OrderConcurrencyIT {

	private static final int WORKERS = 8;
	private static final long TIMEOUT_SECONDS = 30;

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

	@BeforeEach
	void setUp() {
		DatabaseCleanup.deleteAllData(jdbcTemplate);
		userService.ensureBootstrapAdmin("race.admin@example.com", "race admin password");
		adminId = userService.findByEmail("race.admin@example.com").orElseThrow().getId();
	}

	@AfterEach
	void cleanUp() {
		DatabaseCleanup.deleteAllData(jdbcTemplate);
	}

	@Test
	void customersRacingForTheLastUnitsNeverOversell() throws Exception {
		int unitsInStock = 3;
		long productId = createProduct("LAST-UNITS-1", "Limited edition", "10.00");
		restock(productId, unitsInStock);
		List<String> tokens = new ArrayList<>();
		for (int i = 0; i < WORKERS; i++) {
			tokens.add(customerToken(registerCustomer("racer" + i + "@example.com")));
		}

		List<Outcome> outcomes = runConcurrently(worker ->
				placeOrder(tokens.get(worker), "last-units-" + worker, order(item(productId, 1))));

		assertThat(outcomes).filteredOn(Outcome::isCreated).hasSize(unitsInStock);
		assertThat(outcomes).filteredOn(outcome -> !outcome.isCreated())
				.hasSize(WORKERS - unitsInStock)
				.allMatch(outcome -> outcome.status() == 409 && "INSUFFICIENT_STOCK".equals(outcome.code()));

		assertThat(stock(productId)).isZero();
		assertThat(count("orders")).isEqualTo(unitsInStock);
		assertThat(count("order_items")).isEqualTo(unitsInStock);
		assertThat(orderPlacedMovementOrderIds()).containsExactlyInAnyOrderElementsOf(createdOrderIds(outcomes));
	}

	@Test
	void identicalRequestsWithTheSameKeyPlaceExactlyOneOrder() throws Exception {
		long keyboardId = createProduct("RACE-KEYBOARD", "Keyboard", "49.90");
		long cableId = createProduct("RACE-CABLE", "Cable", "0.10");
		restock(keyboardId, 10);
		restock(cableId, 10);
		String token = customerToken(registerCustomer("double.click@example.com"));
		Map<String, Object> body = order(item(keyboardId, 2), item(cableId, 1));

		List<Outcome> outcomes = runConcurrently(worker -> placeOrder(token, "double-click", body));

		assertThat(outcomes).allMatch(outcome -> outcome.isCreated()
				|| (outcome.status() == 409 && "IDEMPOTENCY_KEY_IN_USE".equals(outcome.code())));
		assertThat(outcomes).filteredOn(outcome -> outcome.isCreated() && !outcome.replayed()).hasSize(1);
		Set<Long> orderIds = createdOrderIds(outcomes);
		assertThat(orderIds).hasSize(1);
		long orderId = orderIds.iterator().next();

		assertThat(count("orders")).isEqualTo(1);
		assertThat(count("order_items")).isEqualTo(2);
		assertThat(stock(keyboardId)).isEqualTo(8);
		assertThat(stock(cableId)).isEqualTo(9);
		assertThat(orderPlacedMovementOrderIds()).containsExactly(orderId, orderId);

		assertThat(placeOrder(token, "double-click", body)).isEqualTo(new Outcome(201, null, orderId, true));
	}

	@Test
	void parallelStockAdjustmentsAreAllApplied() throws Exception {
		long productId = createProduct("PARALLEL-1", "Parallel restock", "1.00");
		restock(productId, 5);
		String adminToken = jwtTokenService.issue(adminId, Role.ADMIN).value();

		List<Integer> quantitiesSeen = runConcurrently(worker -> given().port(port)
				.auth().oauth2(adminToken)
				.contentType(ContentType.JSON)
				.body(Map.of("quantityChange", 1, "reason", "RESTOCK"))
			.when()
				.post("/api/v1/admin/inventory/{productId}/adjustments", productId)
			.then()
				.statusCode(200)
				.extract().jsonPath().getInt("quantityOnHand"));

		// Every adjustment saw a different stock level, so none of them overwrote another one.
		assertThat(quantitiesSeen)
				.containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(6, 5 + WORKERS).boxed().toList());
		assertThat(stock(productId)).isEqualTo(5 + WORKERS);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM inventory_movements WHERE product_id = ? AND reason = 'RESTOCK' "
						+ "AND quantity_change = 1",
				Integer.class, productId)).isEqualTo(WORKERS);
	}

	private <T> List<T> runConcurrently(IntFunction<T> work) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(WORKERS);
		CountDownLatch ready = new CountDownLatch(WORKERS);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<T>> futures = new ArrayList<>();
			for (int i = 0; i < WORKERS; i++) {
				int worker = i;
				futures.add(executor.submit(() -> {
					ready.countDown();
					if (!start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
						throw new IllegalStateException("The start signal was never given");
					}
					return work.apply(worker);
				}));
			}
			assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).as("all workers are ready").isTrue();
			start.countDown();

			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				results.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
			}
			return results;
		}
		finally {
			executor.shutdownNow();
		}
	}

	private Outcome placeOrder(String token, String idempotencyKey, Object body) {
		Response response = given().port(port)
				.auth().oauth2(token)
				.header("Idempotency-Key", idempotencyKey)
				.contentType(ContentType.JSON)
				.body(body)
			.when()
				.post("/api/v1/orders");
		boolean created = response.statusCode() == 201;
		return new Outcome(response.statusCode(), response.jsonPath().getString("code"),
				created ? response.jsonPath().getLong("id") : null,
				"true".equals(response.header("Idempotent-Replayed")));
	}

	private static Set<Long> createdOrderIds(List<Outcome> outcomes) {
		return outcomes.stream()
				.filter(Outcome::isCreated)
				.map(Outcome::orderId)
				.collect(Collectors.toSet());
	}

	private List<Long> orderPlacedMovementOrderIds() {
		return jdbcTemplate.queryForList(
				"SELECT order_id FROM inventory_movements WHERE reason = 'ORDER_PLACED'", Long.class);
	}

	private long registerCustomer(String email) {
		return userService.registerCustomer(email, "race customer password").getId();
	}

	private String customerToken(long customerId) {
		return jwtTokenService.issue(customerId, Role.CUSTOMER).value();
	}

	private long createProduct(String sku, String name, String price) {
		return productService.create(new CreateProductRequest(sku, name, null, new BigDecimal(price))).getId();
	}

	private void restock(long productId, int quantity) {
		inventoryService.adjust(productId, quantity, MovementReason.RESTOCK, null, adminId);
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

	private record Outcome(int status, String code, Long orderId, boolean replayed) {

		boolean isCreated() {
			return status == 201;
		}
	}
}
