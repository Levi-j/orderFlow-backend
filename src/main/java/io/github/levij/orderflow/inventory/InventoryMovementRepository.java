package io.github.levij.orderflow.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

	Page<InventoryMovement> findByProductId(Long productId, Pageable pageable);
}
