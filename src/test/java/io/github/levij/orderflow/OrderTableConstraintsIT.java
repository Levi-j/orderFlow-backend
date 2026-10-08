package io.github.levij.orderflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.levij.orderflow.support.DatabaseCleanup;
import io.github.levij.orderflow.support.TestcontainersConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OrderTableConstraintsIT {

	private static final long UNKNOWN_ID = 999_999L;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private long customerId;
	private long productId;
	private long orderId;

	@BeforeEach
	void setUp() {
		DatabaseCleanup.deleteAllData(jdbcTemplate);
		customerId = jdbcTemplate.queryForObject(
				"INSERT INTO users (email, password_hash, role, created_at, updated_at) "
						+ "VALUES ('order.customer@example.com', 'not-a-real-hash', 'CUSTOMER', now(), now()) RETURNING id",
				Long.class);
		productId = jdbcTemplate.queryForObject(
				"INSERT INTO products (sku, name, price, created_at, updated_at) "
						+ "VALUES ('ORDER-1', 'Order test', 2.50, now(), now()) RETURNING id",
				Long.class);
		orderId = insertOrder(customerId, "PENDING", "5.00");
	}

	@AfterEach
	void cleanUp() {
		DatabaseCleanup.deleteAllData(jdbcTemplate);
	}

	@Test
	void orderStatusMustBeKnownAndTotalCannotBeNegative() {
		assertThatThrownBy(() -> insertOrder(customerId, "SHIPPED", "5.00"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertOrder(customerId, "PENDING", "-0.01"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void itemQuantityMustBeBetweenOneAndOneThousand() {
		assertThatThrownBy(() -> insertItem(orderId, productId, "2.50", 0, "0.00"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertItem(orderId, productId, "2.50", 1001, "2502.50"))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThatCode(() -> insertItem(orderId, productId, "2.50", 1000, "2500.00")).doesNotThrowAnyException();
	}

	@Test
	void lineTotalMustEqualUnitPriceTimesQuantity() {
		assertThatThrownBy(() -> insertItem(orderId, productId, "2.50", 2, "4.99"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void productCanAppearOnlyOncePerOrder() {
		insertItem(orderId, productId, "2.50", 1, "2.50");

		assertThatThrownBy(() -> insertItem(orderId, productId, "2.50", 1, "2.50"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void moneyColumnsHoldTheLargestValidOrder() {
		assertThatCode(() -> insertItem(orderId, productId, "9999999999.99", 1000, "9999999999990.00"))
				.doesNotThrowAnyException();
		assertThatCode(() -> insertOrder(customerId, "PENDING", "499999999999500.00")).doesNotThrowAnyException();
	}

	@Test
	void ordersItemsAndMovementsMustReferenceExistingRows() {
		assertThatThrownBy(() -> insertOrder(UNKNOWN_ID, "PENDING", "1.00"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertItem(UNKNOWN_ID, productId, "2.50", 1, "2.50"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertItem(orderId, UNKNOWN_ID, "2.50", 1, "2.50"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertMovement(UNKNOWN_ID))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThatCode(() -> insertMovement(orderId)).doesNotThrowAnyException();
		assertThatCode(() -> insertMovement(null)).doesNotThrowAnyException();
	}

	@Test
	void versionIsARequiredBigintThatStartsAtZero() {
		Map<String, Object> column = jdbcTemplate.queryForMap(
				"SELECT data_type, is_nullable, column_default FROM information_schema.columns "
						+ "WHERE table_name = 'orders' AND column_name = 'version'");
		assertThat(column).containsEntry("data_type", "bigint")
				.containsEntry("is_nullable", "NO")
				.containsEntry("column_default", "0");

		assertThat(jdbcTemplate.queryForObject("SELECT version FROM orders WHERE id = ?", Long.class, orderId))
				.isZero();
		assertThatThrownBy(() -> jdbcTemplate.update("UPDATE orders SET version = NULL WHERE id = ?", orderId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void statusIndexSupportsTheAdminFilter() {
		String definition = jdbcTemplate.queryForObject(
				"SELECT indexdef FROM pg_indexes WHERE tablename = 'orders' AND indexname = 'ix_orders_status_created_at'",
				String.class);

		assertThat(definition).endsWith("(status, created_at DESC)");
	}

	private long insertOrder(long customerId, String status, String totalAmount) {
		return jdbcTemplate.queryForObject(
				"INSERT INTO orders (customer_id, status, total_amount, created_at, updated_at) "
						+ "VALUES (?, ?, ?, now(), now()) RETURNING id",
				Long.class, customerId, status, new BigDecimal(totalAmount));
	}

	private void insertItem(long orderId, long productId, String unitPrice, int quantity, String lineTotal) {
		jdbcTemplate.update(
				"INSERT INTO order_items (order_id, product_id, product_sku, product_name, unit_price, quantity, line_total) "
						+ "VALUES (?, ?, 'ORDER-1', 'Order test', ?, ?, ?)",
				orderId, productId, new BigDecimal(unitPrice), quantity, new BigDecimal(lineTotal));
	}

	private void insertMovement(Long orderId) {
		jdbcTemplate.update(
				"INSERT INTO inventory_movements (product_id, quantity_change, reason, order_id, performed_by_user_id, created_at) "
						+ "VALUES (?, -1, 'ORDER_PLACED', ?, ?, now())",
				productId, orderId, customerId);
	}
}
