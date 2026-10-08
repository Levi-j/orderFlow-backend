package io.github.levij.orderflow.order.dto;

import java.math.BigDecimal;

import io.github.levij.orderflow.order.OrderItem;

public record OrderItemResponse(
		Long productId,
		String productSku,
		String productName,
		BigDecimal unitPrice,
		int quantity,
		BigDecimal lineTotal) {

	public static OrderItemResponse from(OrderItem item) {
		return new OrderItemResponse(
				item.getProductId(),
				item.getProductSku(),
				item.getProductName(),
				item.getUnitPrice(),
				item.getQuantity(),
				item.getLineTotal());
	}
}
