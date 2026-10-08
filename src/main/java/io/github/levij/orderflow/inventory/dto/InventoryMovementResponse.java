package io.github.levij.orderflow.inventory.dto;

import java.time.Instant;

import io.github.levij.orderflow.inventory.InventoryMovement;
import io.github.levij.orderflow.inventory.MovementReason;

public record InventoryMovementResponse(
		Long id,
		Long productId,
		int quantityChange,
		MovementReason reason,
		Long orderId,
		Long performedByUserId,
		String note,
		Instant createdAt) {

	public static InventoryMovementResponse from(InventoryMovement movement) {
		return new InventoryMovementResponse(
				movement.getId(),
				movement.getProductId(),
				movement.getQuantityChange(),
				movement.getReason(),
				movement.getOrderId(),
				movement.getPerformedByUserId(),
				movement.getNote(),
				movement.getCreatedAt());
	}
}
