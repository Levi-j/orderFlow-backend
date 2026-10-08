package io.github.levij.orderflow.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(

		@NotNull
		@Positive
		Long productId,

		@NotNull
		@Min(1)
		@Max(1000)
		Integer quantity) {
}
