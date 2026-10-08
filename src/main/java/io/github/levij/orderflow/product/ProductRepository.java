package io.github.levij.orderflow.product;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProductRepository extends JpaRepository<Product, Long> {

	Page<Product> findByActiveTrue(Pageable pageable);

	Optional<Product> findByIdAndActiveTrue(Long id);

	List<Product> findByIdInAndActiveTrue(Collection<Long> ids);

	boolean existsBySku(String sku);
}
