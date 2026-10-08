package io.github.levij.orderflow.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import io.github.levij.orderflow.support.TestcontainersConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@Transactional
class InventoryItemRepositoryIT {

	@Autowired
	private InventoryItemRepository inventoryItemRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private long productId;

	@BeforeEach
	void createProduct() {
		productId = jdbcTemplate.queryForObject(
				"INSERT INTO products (sku, name, price, created_at, updated_at) "
						+ "VALUES ('ATOMIC-1', 'Atomic test', 1.00, now(), now()) RETURNING id",
				Long.class);
	}

	@Test
	void createIfMissingCreatesTheRowOnlyOnce() {
		assertThat(inventoryItemRepository.createIfMissing(productId, Instant.now())).isEqualTo(1);
		assertThat(inventoryItemRepository.createIfMissing(productId, Instant.now())).isZero();

		assertThat(quantityOnHand()).isZero();
	}

	@Test
	void conditionalUpdateAppliesChangesButNeverGoesBelowZero() {
		inventoryItemRepository.createIfMissing(productId, Instant.now());
		inventoryItemRepository.applyChange(productId, 5, Instant.now());

		assertThat(inventoryItemRepository.applyChange(productId, -4, Instant.now())).isEqualTo(1);
		assertThat(quantityOnHand()).isEqualTo(1);

		assertThat(inventoryItemRepository.applyChange(productId, -2, Instant.now())).isZero();
		assertThat(quantityOnHand()).isEqualTo(1);
	}

	private int quantityOnHand() {
		return jdbcTemplate.queryForObject(
				"SELECT quantity_on_hand FROM inventory_items WHERE product_id = ?", Integer.class, productId);
	}
}
