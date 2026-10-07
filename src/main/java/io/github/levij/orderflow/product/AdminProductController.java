package io.github.levij.orderflow.product;

import java.net.URI;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.github.levij.orderflow.common.config.OpenApiConfig;
import io.github.levij.orderflow.common.web.PageResponse;
import io.github.levij.orderflow.product.dto.CreateProductRequest;
import io.github.levij.orderflow.product.dto.ProductResponse;
import io.github.levij.orderflow.product.dto.UpdateProductRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Admin Products", description = "Product management. Requires a token for a user with the ADMIN role.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/admin/products")
public class AdminProductController {

	private final ProductService productService;

	public AdminProductController(ProductService productService) {
		this.productService = productService;
	}

	@PostMapping
	public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
		Product product = productService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(product.getId())
				.toUri();
		return ResponseEntity.created(location).body(ProductResponse.from(product));
	}

	@PutMapping("/{id}")
	public ProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
		return ProductResponse.from(productService.updateForAdmin(id, request));
	}

	@GetMapping
	public PageResponse<ProductResponse> list(
			@ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
		return PageResponse.from(productService.listForAdmin(pageable).map(ProductResponse::from));
	}

	@GetMapping("/{id}")
	public ProductResponse get(@PathVariable Long id) {
		return ProductResponse.from(productService.getForAdmin(id));
	}
}
