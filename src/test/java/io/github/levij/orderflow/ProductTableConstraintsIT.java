package io.github.levij.orderflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.levij.orderflow.support.TestcontainersConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ProductTableConstraintsIT {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanProducts() {
		jdbcTemplate.update("DELETE FROM products");
	}

	@Test
	void validProductCanBeInserted() {
		insertProduct("ABC-123", new BigDecimal("19.99"));

		Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class);
		assertThat(count).isEqualTo(1);
	}

	@Test
	void duplicateSkuIsRejected() {
		insertProduct("ABC-123", new BigDecimal("19.99"));

		assertThatThrownBy(() -> insertProduct("ABC-123", new BigDecimal("5.00")))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void lowercaseSkuIsRejected() {
		assertThatThrownBy(() -> insertProduct("abc-123", new BigDecimal("19.99")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void skuWithInvalidCharacterIsRejected() {
		assertThatThrownBy(() -> insertProduct("ABC_123", new BigDecimal("19.99")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void zeroPriceIsRejected() {
		assertThatThrownBy(() -> insertProduct("ABC-123", BigDecimal.ZERO))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void negativePriceIsRejected() {
		assertThatThrownBy(() -> insertProduct("ABC-123", new BigDecimal("-1.00")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private void insertProduct(String sku, BigDecimal price) {
		jdbcTemplate.update(
				"INSERT INTO products (sku, name, price, created_at, updated_at) VALUES (?, ?, ?, now(), now())",
				sku, "Test product", price);
	}

}
