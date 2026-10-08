package io.github.levij.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.levij.orderflow.common.error.ConflictException;
import io.github.levij.orderflow.common.error.ErrorCode;
import io.github.levij.orderflow.inventory.InventoryService;
import io.github.levij.orderflow.inventory.MovementReason;
import io.github.levij.orderflow.order.dto.CreateOrderRequest;
import io.github.levij.orderflow.order.dto.OrderItemRequest;
import io.github.levij.orderflow.product.ProductService;
import io.github.levij.orderflow.product.dto.CreateProductRequest;
import io.github.levij.orderflow.support.DatabaseCleanup;
import io.github.levij.orderflow.support.TestcontainersConfiguration;
import io.github.levij.orderflow.user.UserService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OrderIdempotencyIT {

	private static final String KEY = "checkout-42";
	private static final long TIMEOUT_SECONDS = 30;

	@Autowired
	private OrderService orderService;

	@MockitoSpyBean
	private InventoryService inventoryService;

	@MockitoSpyBean
	private ProductService productService;

	@Autowired
	private UserService userService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private long customerId;
	private long productId;
	private CreateOrderRequest request;

	@BeforeEach
	void setUp() {
		DatabaseCleanup.deleteAllData(jdbcTemplate);
		userService.ensureBootstrapAdmin("idempotency.admin@example.com", "idempotency admin password");
		long adminId = userService.findByEmail("idempotency.admin@example.com").orElseThrow().getId();
		customerId = userService.registerCustomer("idempotency.customer@example.com", "idempotency customer password")
				.getId();
		productId = productService.create(new CreateProductRequest("RETRY-1", "Retried product", null,
				new BigDecimal("5.00"))).getId();
		inventoryService.adjust(productId, 10, MovementReason.RESTOCK, null, adminId);
		request = new CreateOrderRequest(List.of(new OrderItemRequest(productId, 2)));
	}

	@AfterEach
	void cleanUp() {
		DatabaseCleanup.deleteAllData(jdbcTemplate);
	}

	@Test
	void replayNeverTouchesInventoryOrCurrentProductData() {
		OrderPlacement first = orderService.placeOrder(customerId, KEY, request);
		clearInvocations(inventoryService, productService);

		OrderPlacement replay = orderService.placeOrder(customerId, KEY, request);

		assertThat(replay.replayed()).isTrue();
		assertThat(replay.order().getId()).isEqualTo(first.order().getId());
		verify(inventoryService, never()).decreaseForOrder(anyLong(), anyInt(), anyLong(), anyLong());
		verify(productService, never()).findActiveByIds(anyCollection());
		assertThat(orderCount()).isEqualTo(1);
		assertThat(stock()).isEqualTo(8);
		assertThat(orderPlacedMovementCount()).isEqualTo(1);
	}

	@Test
	void duplicateThatLosesTheInsertRaceGetsKeyInUseWithoutTouchingStock() throws Exception {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<OrderPlacement> duplicate = startDuplicateWhileFirstAttemptIsUncommitted(executor, true);

			assertThatThrownBy(() -> duplicate.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
					.isInstanceOf(ExecutionException.class)
					.cause()
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_IN_USE));
		}
		finally {
			executor.shutdownNow();
		}

		verify(inventoryService, times(1)).decreaseForOrder(anyLong(), anyInt(), anyLong(), anyLong());
		assertThat(orderCount()).isEqualTo(1);
		assertThat(stock()).isEqualTo(8);
		assertThat(orderPlacedMovementCount()).isEqualTo(1);

		OrderPlacement retry = orderService.placeOrder(customerId, KEY, request);
		assertThat(retry.replayed()).isTrue();
		verify(inventoryService, times(1)).decreaseForOrder(anyLong(), anyInt(), anyLong(), anyLong());
	}

	@Test
	void waitingDuplicatePlacesTheOrderWhenTheFirstAttemptRollsBack() throws Exception {
		OrderPlacement placement;
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<OrderPlacement> duplicate = startDuplicateWhileFirstAttemptIsUncommitted(executor, false);
			placement = duplicate.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		}
		finally {
			executor.shutdownNow();
		}

		assertThat(placement.replayed()).isFalse();
		assertThat(jdbcTemplate.queryForObject("SELECT id FROM orders WHERE idempotency_key = ?", Long.class, KEY))
				.isEqualTo(placement.order().getId());
		assertThat(orderCount()).isEqualTo(1);
		assertThat(stock()).isEqualTo(8);
		assertThat(orderPlacedMovementCount()).isEqualTo(1);
		verify(inventoryService, times(2)).decreaseForOrder(anyLong(), anyInt(), anyLong(), anyLong());
	}

	private Future<OrderPlacement> startDuplicateWhileFirstAttemptIsUncommitted(ExecutorService executor,
			boolean commitFirstAttempt) {
		CountDownLatch duplicatePassedTheIdempotencyCheck = new CountDownLatch(1);
		return new TransactionTemplate(transactionManager).execute(firstAttempt -> {
			orderService.placeOrder(customerId, KEY, request);

			doAnswer(invocation -> {
				duplicatePassedTheIdempotencyCheck.countDown();
				return invocation.callRealMethod();
			}).when(productService).findActiveByIds(anyCollection());
			Future<OrderPlacement> duplicate = executor.submit(() -> orderService.placeOrder(customerId, KEY, request));

			await(duplicatePassedTheIdempotencyCheck);
			if (!commitFirstAttempt) {
				firstAttempt.setRollbackOnly();
			}
			return duplicate;
		});
	}

	private static void await(CountDownLatch latch) {
		try {
			assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
					.as("the duplicate request passed the idempotency check")
					.isTrue();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	private int orderCount() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
	}

	private int stock() {
		return jdbcTemplate.queryForObject(
				"SELECT quantity_on_hand FROM inventory_items WHERE product_id = ?", Integer.class, productId);
	}

	private int orderPlacedMovementCount() {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM inventory_movements WHERE reason = 'ORDER_PLACED'", Integer.class);
	}
}
