package io.github.levij.orderflow.inventory;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

	@Modifying
	@Query(value = """
			INSERT INTO inventory_items (product_id, quantity_on_hand, updated_at)
			VALUES (:productId, 0, :now)
			ON CONFLICT (product_id) DO NOTHING
			""", nativeQuery = true)
	int createIfMissing(@Param("productId") Long productId, @Param("now") Instant now);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE inventory_items
			SET quantity_on_hand = quantity_on_hand + :change,
			    updated_at = :now
			WHERE product_id = :productId
			  AND quantity_on_hand + :change >= 0
			""", nativeQuery = true)
	int applyChange(@Param("productId") Long productId, @Param("change") int change, @Param("now") Instant now);
}
