package io.github.levij.orderflow.product;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.levij.orderflow.common.web.PageResponse;
import io.github.levij.orderflow.product.dto.ProductResponse;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

	private final ProductService productService;

	public ProductController(ProductService productService) {
		this.productService = productService;
	}

	@GetMapping
	public PageResponse<ProductResponse> list(@PageableDefault(size = 20, sort = "name") Pageable pageable) {
		return PageResponse.from(productService.listActive(pageable).map(ProductResponse::from));
	}

	@GetMapping("/{id}")
	public ProductResponse get(@PathVariable Long id) {
		return ProductResponse.from(productService.getActive(id));
	}
}
