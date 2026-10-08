package io.github.levij.orderflow.inventory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.levij.orderflow.common.error.ConflictException;
import io.github.levij.orderflow.common.error.ErrorCode;
import io.github.levij.orderflow.product.ProductService;

@Service
public class InventoryService {

	private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

	private final ProductService productService;
	private final InventoryItemRepository inventoryItemRepository;
	private final InventoryMovementRepository inventoryMovementRepository;

	public InventoryService(ProductService productService, InventoryItemRepository inventoryItemRepository,
			InventoryMovementRepository inventoryMovementRepository) {
		this.productService = productService;
		this.inventoryItemRepository = inventoryItemRepository;
		this.inventoryMovementRepository = inventoryMovementRepository;
	}

	@Transactional(readOnly = true)
	public int getQuantityOnHand(Long productId) {
		requireProduct(productId);
		return currentQuantity(productId);
	}

	@Transactional
	public int adjust(Long productId, int quantityChange, MovementReason reason, String note, Long performedByUserId) {
		requireProduct(productId);
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

		changeStock(productId, quantityChange, now, "There is not enough stock for this change.");

		inventoryMovementRepository.save(
				InventoryMovement.manualAdjustment(productId, quantityChange, reason, performedByUserId, note, now));

		int quantityOnHand = currentQuantity(productId);
		log.atInfo()
				.addKeyValue("eventName", "inventory.adjusted")
				.addKeyValue("productId", productId)
				.addKeyValue("quantityChange", quantityChange)
				.addKeyValue("reason", reason)
				.addKeyValue("performedByUserId", performedByUserId)
				.addKeyValue("quantityOnHand", quantityOnHand)
				.log("Inventory adjusted");
		return quantityOnHand;
	}

	@Transactional
	public void decreaseForOrder(Long productId, int quantity, Long orderId, Long customerId) {
		if (quantity <= 0) {
			throw new IllegalArgumentException("Order quantity must be positive");
		}
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

		changeStock(productId, -quantity, now, "Insufficient stock for product " + productId + ".");

		inventoryMovementRepository.save(InventoryMovement.orderPlaced(productId, quantity, orderId, customerId, now));
	}

	@Transactional
	public void restoreForCancelledOrder(Long productId, int quantity, Long orderId, Long performedByUserId) {
		if (quantity <= 0) {
			throw new IllegalArgumentException("Restored quantity must be positive");
		}
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

		changeStock(productId, quantity, now, "Stock for product " + productId + " could not be restored.");

		inventoryMovementRepository.save(
				InventoryMovement.orderCancelled(productId, quantity, orderId, performedByUserId, now));
	}

	@Transactional(readOnly = true)
	public Page<InventoryMovement> listMovements(Long productId, Pageable pageable) {
		requireProduct(productId);
		return inventoryMovementRepository.findByProductId(productId, pageable);
	}

	private void changeStock(Long productId, int quantityChange, Instant now, String insufficientStockMessage) {
		inventoryItemRepository.createIfMissing(productId, now);

		if (inventoryItemRepository.applyChange(productId, quantityChange, now) == 0) {
			log.atWarn()
					.addKeyValue("eventName", "inventory.insufficient_stock")
					.addKeyValue("productId", productId)
					.addKeyValue("quantityChange", quantityChange)
					.log("Insufficient stock");
			throw new ConflictException(ErrorCode.INSUFFICIENT_STOCK, insufficientStockMessage);
		}
	}

	private void requireProduct(Long productId) {
		productService.getForAdmin(productId);
	}

	private int currentQuantity(Long productId) {
		return inventoryItemRepository.findById(productId)
				.map(InventoryItem::getQuantityOnHand)
				.orElse(0);
	}
}
