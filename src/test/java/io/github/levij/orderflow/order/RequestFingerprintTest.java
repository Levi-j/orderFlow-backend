package io.github.levij.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.levij.orderflow.order.dto.OrderItemRequest;

class RequestFingerprintTest {

	@Test
	void hashIsTheSha256OfTheSortedProductAndQuantityLines() {
		String hash = RequestFingerprint.of(List.of(item(20, 3), item(1, 2), item(5, 1)));

		assertThat(hash)
				.isEqualTo("6ab8ac8911559375cf59f7cfc9d70d18faf1cfedd555d78aad51887f869952b2")
				.hasSize(64)
				.matches("[0-9a-f]{64}");
	}

	@Test
	void sameItemsGiveTheSameHash() {
		assertThat(RequestFingerprint.of(List.of(item(1, 2), item(5, 1))))
				.isEqualTo(RequestFingerprint.of(List.of(item(1, 2), item(5, 1))));
	}

	@Test
	void itemOrderDoesNotMatter() {
		assertThat(RequestFingerprint.of(List.of(item(5, 1), item(1, 2))))
				.isEqualTo(RequestFingerprint.of(List.of(item(1, 2), item(5, 1))));
	}

	@Test
	void changedQuantityGivesADifferentHash() {
		assertThat(RequestFingerprint.of(List.of(item(1, 2), item(5, 1))))
				.isNotEqualTo(RequestFingerprint.of(List.of(item(1, 3), item(5, 1))));
	}

	@Test
	void changedProductGivesADifferentHash() {
		assertThat(RequestFingerprint.of(List.of(item(1, 2), item(5, 1))))
				.isNotEqualTo(RequestFingerprint.of(List.of(item(1, 2), item(6, 1))));
	}

	private static OrderItemRequest item(long productId, int quantity) {
		return new OrderItemRequest(productId, quantity);
	}
}
