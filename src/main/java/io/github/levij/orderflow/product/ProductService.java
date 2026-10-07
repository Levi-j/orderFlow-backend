package io.github.levij.orderflow.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.levij.orderflow.common.error.NotFoundException;
import io.github.levij.orderflow.product.dto.CreateProductRequest;

@Service
public class ProductService {

	private final ProductRepository productRepository;

	public ProductService(ProductRepository productRepository) {
		this.productRepository = productRepository;
	}

	@Transactional
	public Product create(CreateProductRequest request) {
		Product product = Product.create(
				request.sku(),
				request.name(),
				request.description(),
				request.price());
		return productRepository.save(product);
	}

	@Transactional(readOnly = true)
	public Product getForAdmin(Long id) {
		return productRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
	}

	@Transactional(readOnly = true)
	public Page<Product> listForAdmin(Pageable pageable) {
		return productRepository.findAll(pageable);
	}

	@Transactional(readOnly = true)
	public Product getActive(Long id) {
		return productRepository.findByIdAndActiveTrue(id)
				.orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
	}

	@Transactional(readOnly = true)
	public Page<Product> listActive(Pageable pageable) {
		return productRepository.findByActiveTrue(pageable);
	}
}
