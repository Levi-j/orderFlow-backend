package io.github.levij.orderflow.order;

public record OrderPlacement(CustomerOrder order, boolean replayed) {

	static OrderPlacement created(CustomerOrder order) {
		return new OrderPlacement(order, false);
	}

	static OrderPlacement replay(CustomerOrder order) {
		return new OrderPlacement(order, true);
	}
}
