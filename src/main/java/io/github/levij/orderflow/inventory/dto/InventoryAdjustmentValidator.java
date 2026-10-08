package io.github.levij.orderflow.inventory.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class InventoryAdjustmentValidator
		implements ConstraintValidator<ValidInventoryAdjustment, InventoryAdjustmentRequest> {

	@Override
	public boolean isValid(InventoryAdjustmentRequest request, ConstraintValidatorContext context) {
		if (request == null || request.quantityChange() == null) {
			return true;
		}

		String problem = null;
		if (request.quantityChange() == 0) {
			problem = "quantityChange must not be zero";
		}
		else if (request.reason() == AdjustmentReason.RESTOCK && request.quantityChange() < 0) {
			problem = "quantityChange must be positive for RESTOCK";
		}

		if (problem == null) {
			return true;
		}

		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(problem)
				.addPropertyNode("quantityChange")
				.addConstraintViolation();
		return false;
	}
}
