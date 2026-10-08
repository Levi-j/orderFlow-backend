package io.github.levij.orderflow.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@ValidInventoryAdjustment
public record InventoryAdjustmentRequest(

		@NotNull
		Integer quantityChange,

		@NotNull
		AdjustmentReason reason,

		@Size(max = 500)
		String note) {
}
