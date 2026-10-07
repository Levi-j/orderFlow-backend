package io.github.levij.orderflow.product;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProductRepository extends JpaRepository<Product, Long> {

	Page<Product> findByActiveTrue(Pageable pageable);

	Optional<Product> findByIdAndActiveTrue(Long id);
}
