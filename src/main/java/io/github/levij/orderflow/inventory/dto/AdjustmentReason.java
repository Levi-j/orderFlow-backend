package io.github.levij.orderflow.inventory.dto;

import io.github.levij.orderflow.inventory.MovementReason;

public enum AdjustmentReason {
	RESTOCK,
	ADJUSTMENT;

	public MovementReason toMovementReason() {
		return switch (this) {
			case RESTOCK -> MovementReason.RESTOCK;
			case ADJUSTMENT -> MovementReason.ADJUSTMENT;
		};
	}
}
