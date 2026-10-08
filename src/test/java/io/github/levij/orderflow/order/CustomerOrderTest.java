package io.github.levij.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.levij.orderflow.common.error.ConflictException;
import io.github.levij.orderflow.common.error.ErrorCode;

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

	@ParameterizedTest(name = "PENDING + {0} -> {1}")
	@CsvSource({
			"CONFIRM, CONFIRMED",
			"CANCEL,  CANCELLED" })
	void pendingOrderCanBeConfirmedOrCancelled(Transition transition, OrderStatus expected) {
		CustomerOrder order = orderWithStatus(OrderStatus.PENDING);
		Instant createdAt = order.getCreatedAt();

		transition.applyTo(order);

		assertThat(order.getStatus()).isEqualTo(expected);
		assertThat(order.getCreatedAt()).isEqualTo(createdAt);
		assertThat(order.getUpdatedAt()).isAfterOrEqualTo(createdAt);
	}

	@ParameterizedTest(name = "{0} + {1} is rejected")
	@CsvSource({
			"CONFIRMED, CONFIRM",
			"CONFIRMED, CANCEL",
			"CANCELLED, CONFIRM",
			"CANCELLED, CANCEL" })
	void confirmedAndCancelledOrdersCannotChangeAgain(OrderStatus current, Transition transition) {
		CustomerOrder order = orderWithStatus(current);
		Instant updatedAt = order.getUpdatedAt();

		assertThatThrownBy(() -> transition.applyTo(order))
				.isInstanceOfSatisfying(ConflictException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));

		assertThat(order.getStatus()).isEqualTo(current);
		assertThat(order.getUpdatedAt()).isEqualTo(updatedAt);
	}

	private static CustomerOrder orderWithStatus(OrderStatus status) {
		CustomerOrder order = CustomerOrder.place(42L,
				List.of(OrderItem.create(1L, "KEYBOARD-1", "Keyboard", new BigDecimal("49.90"), 1)));
		switch (status) {
			case PENDING -> {
			}
			case CONFIRMED -> order.confirm();
			case CANCELLED -> order.cancel();
		}
		return order;
	}

	enum Transition {
		CONFIRM,
		CANCEL;

		void applyTo(CustomerOrder order) {
			switch (this) {
				case CONFIRM -> order.confirm();
				case CANCEL -> order.cancel();
			}
		}
	}
}
