package io.github.levij.orderflow.order;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

	Page<CustomerOrder> findByCustomerId(Long customerId, Pageable pageable);

	Page<CustomerOrder> findByStatus(OrderStatus status, Pageable pageable);

	@EntityGraph(attributePaths = "items")
	Optional<CustomerOrder> findWithItemsByIdAndCustomerId(Long id, Long customerId);

	@EntityGraph(attributePaths = "items")
	Optional<CustomerOrder> findWithItemsById(Long id);

	@EntityGraph(attributePaths = "items")
	Optional<CustomerOrder> findWithItemsByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);
}
