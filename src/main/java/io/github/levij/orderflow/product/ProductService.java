package io.github.levij.orderflow.product;

import java.util.Locale;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.levij.orderflow.common.error.ConflictException;
import io.github.levij.orderflow.common.error.ErrorCode;
import io.github.levij.orderflow.common.error.NotFoundException;
import io.github.levij.orderflow.product.dto.CreateProductRequest;
import io.github.levij.orderflow.product.dto.UpdateProductRequest;

@Service
public class ProductService {

	private static final String SKU_UNIQUE_CONSTRAINT = "uk_products_sku";

	private final ProductRepository productRepository;

	public ProductService(ProductRepository productRepository) {
		this.productRepository = productRepository;
	}

	@Transactional
	public Product create(CreateProductRequest request) {
		String sku = request.sku().toUpperCase(Locale.ROOT);

		if (productRepository.existsBySku(sku)) {
			throw duplicateSku(sku);
		}

		Product product = Product.create(sku, request.name(), request.description(), request.price());
		try {
			return productRepository.saveAndFlush(product);
		}
		catch (DataIntegrityViolationException ex) {
			if (violatesSkuUniqueConstraint(ex)) {
				throw duplicateSku(sku);
			}
			throw ex;
		}
	}

	@Transactional
	public Product updateForAdmin(Long id, UpdateProductRequest request) {
		Product product = getForAdmin(id);
		product.updateDetails(request.name(), request.description(), request.price(), request.active());
		return product;
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

	private static ConflictException duplicateSku(String sku) {
		return new ConflictException(ErrorCode.DUPLICATE_SKU, "A product with SKU " + sku + " already exists.");
	}

	private static boolean violatesSkuUniqueConstraint(DataIntegrityViolationException ex) {
		for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation
					&& SKU_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName())) {
				return true;
			}
		}
		return false;
	}
}
