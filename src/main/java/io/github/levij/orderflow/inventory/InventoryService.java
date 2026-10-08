package io.github.levij.orderflow.inventory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.levij.orderflow.common.error.ConflictException;
import io.github.levij.orderflow.common.error.ErrorCode;
import io.github.levij.orderflow.product.ProductService;

@Service
public class InventoryService {

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

		inventoryItemRepository.createIfMissing(productId, now);

		if (inventoryItemRepository.applyChange(productId, quantityChange, now) == 0) {
			throw new ConflictException(ErrorCode.INSUFFICIENT_STOCK,
					"There is not enough stock for this change.");
		}

		inventoryMovementRepository.save(
				InventoryMovement.manualAdjustment(productId, quantityChange, reason, performedByUserId, note, now));

		return currentQuantity(productId);
	}

	@Transactional(readOnly = true)
	public Page<InventoryMovement> listMovements(Long productId, Pageable pageable) {
		requireProduct(productId);
		return inventoryMovementRepository.findByProductId(productId, pageable);
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
