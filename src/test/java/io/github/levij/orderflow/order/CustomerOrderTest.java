package io.github.levij.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class CustomerOrderTest {

	@Test
	void lineTotalIsExactlyUnitPriceTimesQuantity() {
		OrderItem cable = OrderItem.create(1L, "CABLE-1", "Cable", new BigDecimal("0.10"), 3);
		OrderItem keyboard = OrderItem.create(2L, "KEYBOARD-1", "Keyboard", new BigDecimal("49.90"), 7);

		assertThat(cable.getLineTotal()).isEqualByComparingTo("0.30");
		assertThat(keyboard.getLineTotal()).isEqualByComparingTo("349.30");
	}

	@Test
	void placedOrderIsPendingAndItsTotalIsTheSumOfItsLines() {
		OrderItem keyboard = OrderItem.create(1L, "KEYBOARD-1", "Keyboard", new BigDecimal("49.90"), 2);
		OrderItem cable = OrderItem.create(2L, "CABLE-1", "Cable", new BigDecimal("0.10"), 3);

		CustomerOrder order = CustomerOrder.place(42L, List.of(keyboard, cable));

		assertThat(order.getCustomerId()).isEqualTo(42L);
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
		assertThat(order.getTotalAmount()).isEqualByComparingTo("100.10");
		assertThat(order.getItems()).containsExactly(keyboard, cable);
		assertThat(order.getCreatedAt()).isNotNull().isEqualTo(order.getUpdatedAt());
	}
}
