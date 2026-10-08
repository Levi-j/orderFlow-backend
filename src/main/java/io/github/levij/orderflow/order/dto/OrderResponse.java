package io.github.levij.orderflow.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.github.levij.orderflow.order.CustomerOrder;
import io.github.levij.orderflow.order.OrderStatus;

public record OrderResponse(
		Long id,
		Long customerId,
		OrderStatus status,
		BigDecimal totalAmount,
		Instant createdAt,
		Instant updatedAt,
		List<OrderItemResponse> items) {

	public static OrderResponse from(CustomerOrder order) {
		return new OrderResponse(
				order.getId(),
				order.getCustomerId(),
				order.getStatus(),
				order.getTotalAmount(),
				order.getCreatedAt(),
				order.getUpdatedAt(),
				order.getItems().stream().map(OrderItemResponse::from).toList());
	}

	public static OrderResponse asPlaced(CustomerOrder order) {
		return new OrderResponse(
				order.getId(),
				order.getCustomerId(),
				OrderStatus.PENDING,
				order.getTotalAmount(),
				order.getCreatedAt(),
				order.getCreatedAt(),
				order.getItems().stream().map(OrderItemResponse::from).toList());
	}
}
