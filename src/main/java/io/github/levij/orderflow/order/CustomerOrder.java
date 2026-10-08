package io.github.levij.orderflow.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.github.levij.orderflow.common.error.ConflictException;
import io.github.levij.orderflow.common.error.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "orders")
public class CustomerOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "customer_id", nullable = false)
	private Long customerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrderStatus status;

	@Column(name = "total_amount", nullable = false, precision = 17, scale = 2)
	private BigDecimal totalAmount;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	@Column(name = "idempotency_key", length = 100)
	private String idempotencyKey;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "request_hash", length = 64)
	private String requestHash;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id ASC")
	private List<OrderItem> items = new ArrayList<>();

	protected CustomerOrder() {
	}

	private CustomerOrder(Long customerId, String idempotencyKey, String requestHash, List<OrderItem> items) {
		this.customerId = customerId;
		this.idempotencyKey = idempotencyKey;
		this.requestHash = requestHash;
		this.status = OrderStatus.PENDING;
		for (OrderItem item : items) {
			item.attachTo(this);
			this.items.add(item);
		}
		this.totalAmount = items.stream()
				.map(OrderItem::getLineTotal)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
		this.updatedAt = this.createdAt;
	}

	public static CustomerOrder place(Long customerId, String idempotencyKey, String requestHash, List<OrderItem> items) {
		return new CustomerOrder(customerId, idempotencyKey, requestHash, items);
	}

	public void confirm() {
		changeStatus(OrderStatus.CONFIRMED, "confirmed");
	}

	public void cancel() {
		changeStatus(OrderStatus.CANCELLED, "cancelled");
	}

	private void changeStatus(OrderStatus newStatus, String action) {
		if (status != OrderStatus.PENDING) {
			throw new ConflictException(ErrorCode.INVALID_STATUS_TRANSITION,
					"Only PENDING orders can be " + action + ". This order is " + status + ".");
		}
		this.status = newStatus;
		this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	public Long getId() {
		return id;
	}

	public Long getCustomerId() {
		return customerId;
	}

	public OrderStatus getStatus() {
		return status;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public List<OrderItem> getItems() {
		return Collections.unmodifiableList(items);
	}

	String getRequestHash() {
		return requestHash;
	}
}
