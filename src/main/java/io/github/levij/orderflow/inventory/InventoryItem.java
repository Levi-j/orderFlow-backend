package io.github.levij.orderflow.inventory;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {

	@Id
	@Column(name = "product_id")
	private Long productId;

	@Column(name = "quantity_on_hand", nullable = false)
	private int quantityOnHand;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected InventoryItem() {
	}

	public Long getProductId() {
		return productId;
	}

	public int getQuantityOnHand() {
		return quantityOnHand;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
