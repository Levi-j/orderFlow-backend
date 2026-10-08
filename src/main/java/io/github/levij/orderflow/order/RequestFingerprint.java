package io.github.levij.orderflow.order;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

import io.github.levij.orderflow.order.dto.OrderItemRequest;

final class RequestFingerprint {

	private RequestFingerprint() {
	}
	static String of(List<OrderItemRequest> items) {
		String canonical = items.stream()
				.sorted(Comparator.comparing(OrderItemRequest::productId))
				.map(item -> item.productId() + ":" + item.quantity())
				.collect(Collectors.joining("\n"));
		return HexFormat.of().formatHex(sha256(canonical.getBytes(StandardCharsets.UTF_8)));
	}

	private static byte[] sha256(byte[] input) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(input);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}
}
