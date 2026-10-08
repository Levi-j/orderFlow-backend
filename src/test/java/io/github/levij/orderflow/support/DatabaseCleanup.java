package io.github.levij.orderflow.support;

import org.springframework.jdbc.core.JdbcTemplate;

public final class DatabaseCleanup {

	private DatabaseCleanup() {
	}

	public static void deleteInventoryProductsAndUsers(JdbcTemplate jdbcTemplate) {
		jdbcTemplate.update("DELETE FROM inventory_movements");
		jdbcTemplate.update("DELETE FROM inventory_items");
		jdbcTemplate.update("DELETE FROM products");
		jdbcTemplate.update("DELETE FROM users");
	}
}
