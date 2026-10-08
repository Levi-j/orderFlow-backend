package io.github.levij.orderflow.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id ASC")
	private List<OrderItem> items = new ArrayList<>();

	protected CustomerOrder() {
	}

	private CustomerOrder(Long customerId, List<OrderItem> items) {
		this.customerId = customerId;
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

	public static CustomerOrder place(Long customerId, List<OrderItem> items) {
		return new CustomerOrder(customerId, items);
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
}
