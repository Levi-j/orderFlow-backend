package io.github.levij.orderflow.order.dto;

import java.math.BigDecimal;
import java.time.Instant;

import io.github.levij.orderflow.order.CustomerOrder;
import io.github.levij.orderflow.order.OrderStatus;

public record AdminOrderSummaryResponse(
		Long id,
		Long customerId,
		OrderStatus status,
		BigDecimal totalAmount,
		Instant createdAt,
		Instant updatedAt) {

	public static AdminOrderSummaryResponse from(CustomerOrder order) {
		return new AdminOrderSummaryResponse(
				order.getId(),
				order.getCustomerId(),
				order.getStatus(),
				order.getTotalAmount(),
				order.getCreatedAt(),
				order.getUpdatedAt());
	}
}
