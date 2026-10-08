package io.github.levij.orderflow.order.dto;

import java.math.BigDecimal;
import java.time.Instant;

import io.github.levij.orderflow.order.CustomerOrder;
import io.github.levij.orderflow.order.OrderStatus;

public record OrderSummaryResponse(
		Long id,
		OrderStatus status,
		BigDecimal totalAmount,
		Instant createdAt,
		Instant updatedAt) {

	public static OrderSummaryResponse from(CustomerOrder order) {
		return new OrderSummaryResponse(
				order.getId(),
				order.getStatus(),
				order.getTotalAmount(),
				order.getCreatedAt(),
				order.getUpdatedAt());
	}
}
