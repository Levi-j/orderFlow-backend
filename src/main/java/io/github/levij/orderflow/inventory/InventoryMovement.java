package io.github.levij.orderflow.inventory;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory_movements")
public class InventoryMovement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(name = "quantity_change", nullable = false)
	private int quantityChange;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MovementReason reason;

	@Column(name = "order_id")
	private Long orderId;

	@Column(name = "performed_by_user_id", nullable = false)
	private Long performedByUserId;

	@Column(length = 500)
	private String note;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected InventoryMovement() {
	}

	private InventoryMovement(Long productId, int quantityChange, MovementReason reason, Long orderId,
			Long performedByUserId, String note, Instant createdAt) {
		this.productId = productId;
		this.quantityChange = quantityChange;
		this.reason = reason;
		this.orderId = orderId;
		this.performedByUserId = performedByUserId;
		this.note = note;
		this.createdAt = createdAt;
	}

	static InventoryMovement manualAdjustment(Long productId, int quantityChange, MovementReason reason,
			Long performedByUserId, String note, Instant createdAt) {
		return new InventoryMovement(productId, quantityChange, reason, null, performedByUserId, note, createdAt);
	}

	static InventoryMovement orderPlaced(Long productId, int quantity, Long orderId, Long customerId,
			Instant createdAt) {
		return new InventoryMovement(productId, -quantity, MovementReason.ORDER_PLACED, orderId, customerId, null,
				createdAt);
	}

	public Long getId() {
		return id;
	}

	public Long getProductId() {
		return productId;
	}

	public int getQuantityChange() {
		return quantityChange;
	}

	public MovementReason getReason() {
		return reason;
	}

	public Long getOrderId() {
		return orderId;
	}

	public Long getPerformedByUserId() {
		return performedByUserId;
	}

	public String getNote() {
		return note;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
