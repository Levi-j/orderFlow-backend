package io.github.levij.orderflow.order.dto;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class UniqueProductIdsValidator implements ConstraintValidator<UniqueProductIds, List<OrderItemRequest>> {

	@Override
	public boolean isValid(List<OrderItemRequest> items, ConstraintValidatorContext context) {
		if (items == null) {
			return true;
		}

		Set<Long> productIds = new HashSet<>();
		for (OrderItemRequest item : items) {
			if (item != null && item.productId() != null && !productIds.add(item.productId())) {
				return false;
			}
		}
		return true;
	}
}
