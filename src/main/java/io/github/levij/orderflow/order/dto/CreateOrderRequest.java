package io.github.levij.orderflow.order.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(

		@NotNull
		@Size(min = 1, max = 50)
		@UniqueProductIds
		List<@NotNull @Valid OrderItemRequest> items) {
}
