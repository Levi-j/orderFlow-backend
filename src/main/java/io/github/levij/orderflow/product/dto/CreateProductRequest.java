package io.github.levij.orderflow.product.dto;

import java.math.BigDecimal;

public record CreateProductRequest(
		String sku,
		String name,
		String description,
		BigDecimal price) {
}
