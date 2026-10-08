package io.github.levij.orderflow.order;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_items")
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private CustomerOrder order;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(name = "product_sku", nullable = false, length = 64)
	private String productSku;

	@Column(name = "product_name", nullable = false, length = 200)
	private String productName;

	@Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal unitPrice;

	@Column(nullable = false)
	private int quantity;

	@Column(name = "line_total", nullable = false, precision = 15, scale = 2)
	private BigDecimal lineTotal;

	protected OrderItem() {
	}

	private OrderItem(Long productId, String productSku, String productName, BigDecimal unitPrice, int quantity) {
		this.productId = productId;
		this.productSku = productSku;
		this.productName = productName;
		this.unitPrice = unitPrice;
		this.quantity = quantity;
		this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
	}

	public static OrderItem create(Long productId, String productSku, String productName, BigDecimal unitPrice,
			int quantity) {
		return new OrderItem(productId, productSku, productName, unitPrice, quantity);
	}

	void attachTo(CustomerOrder order) {
		this.order = order;
	}

	public Long getId() {
		return id;
	}

	public Long getProductId() {
		return productId;
	}

	public String getProductSku() {
		return productSku;
	}

	public String getProductName() {
		return productName;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public int getQuantity() {
		return quantity;
	}

	public BigDecimal getLineTotal() {
		return lineTotal;
	}
}
