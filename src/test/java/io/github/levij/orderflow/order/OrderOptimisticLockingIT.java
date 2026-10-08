package io.github.levij.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

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
class OrderOptimisticLockingIT {

	@Autowired
	private OrderService orderService;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private UserService userService;

	@Autowired
	private ProductService productService;

	@MockitoSpyBean
	private InventoryService inventoryService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private long adminId;
	private long productId;
	private long orderId;

	@BeforeEach
	void setUp() {
		DatabaseCleanup.deleteAllData(jdbcTemplate);
		userService.ensureBootstrapAdmin("locking.admin@example.com", "locking admin password");
		adminId = userService.findByEmail("locking.admin@example.com").orElseThrow().getId();
		long customerId = userService.registerCustomer("locking.customer@example.com", "locking customer password")
				.getId();
		productId = productService.create(new CreateProductRequest("LOCK-1", "Locked product", null,
				new BigDecimal("5.00"))).getId();
		inventoryService.adjust(productId, 10, MovementReason.RESTOCK, null, adminId);
		orderId = orderService.placeOrder(customerId, "locking-order-1",
				new CreateOrderRequest(List.of(new OrderItemRequest(productId, 4)))).order().getId();
	}

	@AfterEach
	void cleanUp() {
		DatabaseCleanup.deleteAllData(jdbcTemplate);
	}

	@Test
	void staleCancellationFailsBeforeAnyStockIsRestored() {
		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			orderRepository.findWithItemsById(orderId).orElseThrow();
			inAnotherTransaction(() -> orderService.confirm(orderId));
			orderService.cancelForAdmin(orderId, adminId);
		})).isInstanceOf(OptimisticLockingFailureException.class);

		verify(inventoryService, never()).restoreForCancelledOrder(anyLong(), anyInt(), anyLong(), anyLong());
		assertThat(storedStatusAndVersion()).isEqualTo("CONFIRMED/1");
		assertThat(stock()).isEqualTo(6);
		assertThat(cancellationMovementCount()).isZero();
	}

	@Test
	void staleConfirmationIsRejected() {
		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			orderRepository.findWithItemsById(orderId).orElseThrow();
			inAnotherTransaction(() -> orderService.cancelForAdmin(orderId, adminId));
			orderService.confirm(orderId);
		})).isInstanceOf(OptimisticLockingFailureException.class);

		verify(inventoryService, times(1)).restoreForCancelledOrder(productId, 4, orderId, adminId);
		assertThat(storedStatusAndVersion()).isEqualTo("CANCELLED/1");
		assertThat(stock()).isEqualTo(10);
		assertThat(cancellationMovementCount()).isEqualTo(1);
	}

	private void inAnotherTransaction(Runnable work) {
		TransactionTemplate separate = new TransactionTemplate(transactionManager);
		separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		separate.executeWithoutResult(status -> work.run());
	}

	private String storedStatusAndVersion() {
		return jdbcTemplate.queryForObject("SELECT status || '/' || version FROM orders WHERE id = ?", String.class,
				orderId);
	}

	private int stock() {
		return jdbcTemplate.queryForObject(
				"SELECT quantity_on_hand FROM inventory_items WHERE product_id = ?", Integer.class, productId);
	}

	private int cancellationMovementCount() {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM inventory_movements WHERE reason = 'ORDER_CANCELLED'", Integer.class);
	}
}
