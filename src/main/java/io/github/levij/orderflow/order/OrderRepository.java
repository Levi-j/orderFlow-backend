package io.github.levij.orderflow.order;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

	Page<CustomerOrder> findByCustomerId(Long customerId, Pageable pageable);

	@EntityGraph(attributePaths = "items")
	Optional<CustomerOrder> findWithItemsByIdAndCustomerId(Long id, Long customerId);
}
