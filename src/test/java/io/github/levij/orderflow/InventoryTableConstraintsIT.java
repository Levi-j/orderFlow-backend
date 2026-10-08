package io.github.levij.orderflow;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class InventoryTableConstraintsIT {

	private static final long UNKNOWN_ID = 999_999L;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private long productId;
	private long userId;

	@BeforeEach
	void setUp() {
		DatabaseCleanup.deleteAllData(jdbcTemplate);
		productId = jdbcTemplate.queryForObject(
				"INSERT INTO products (sku, name, price, created_at, updated_at) "
						+ "VALUES ('STOCK-1', 'Stock test', 1.00, now(), now()) RETURNING id",
				Long.class);
		userId = jdbcTemplate.queryForObject(
				"INSERT INTO users (email, password_hash, role, created_at, updated_at) "
						+ "VALUES ('stock.admin@example.com', 'not-a-real-hash', 'ADMIN', now(), now()) RETURNING id",
				Long.class);
	}

	@AfterEach
	void cleanUp() {
		DatabaseCleanup.deleteAllData(jdbcTemplate);
	}

	@Test
	void stockCanNeverBeNegative() {
		assertThatThrownBy(() -> insertStock(productId, -1))
				.isInstanceOf(DataIntegrityViolationException.class);

		insertStock(productId, 0);
		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE inventory_items SET quantity_on_hand = -1 WHERE product_id = ?", productId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void movementQuantityChangeCannotBeZero() {
		assertThatCode(() -> insertMovement(productId, 5, "RESTOCK", userId)).doesNotThrowAnyException();

		assertThatThrownBy(() -> insertMovement(productId, 0, "RESTOCK", userId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void unknownMovementReasonIsRejected() {
		assertThatThrownBy(() -> insertMovement(productId, 5, "GIFT", userId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void stockAndMovementsMustReferenceAnExistingProduct() {
		assertThatThrownBy(() -> insertStock(UNKNOWN_ID, 1))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertMovement(UNKNOWN_ID, 1, "RESTOCK", userId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void movementMustReferenceAnExistingUser() {
		assertThatThrownBy(() -> insertMovement(productId, 1, "RESTOCK", UNKNOWN_ID))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private void insertStock(long productId, int quantity) {
		jdbcTemplate.update(
				"INSERT INTO inventory_items (product_id, quantity_on_hand, updated_at) VALUES (?, ?, now())",
				productId, quantity);
	}

	private void insertMovement(long productId, int quantityChange, String reason, long performedByUserId) {
		jdbcTemplate.update(
				"INSERT INTO inventory_movements (product_id, quantity_change, reason, performed_by_user_id, created_at) "
						+ "VALUES (?, ?, ?, ?, now())",
				productId, quantityChange, reason, performedByUserId);
	}
}
