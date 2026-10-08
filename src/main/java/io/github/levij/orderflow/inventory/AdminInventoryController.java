package io.github.levij.orderflow.inventory;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.levij.orderflow.common.config.OpenApiConfig;
import io.github.levij.orderflow.common.web.PageResponse;
import io.github.levij.orderflow.inventory.dto.InventoryAdjustmentRequest;
import io.github.levij.orderflow.inventory.dto.InventoryMovementResponse;
import io.github.levij.orderflow.inventory.dto.InventoryResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Admin Inventory", description = "Stock levels and stock movement history. Requires a token for a user with the ADMIN role.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/admin/inventory")
public class AdminInventoryController {

	private final InventoryService inventoryService;

	public AdminInventoryController(InventoryService inventoryService) {
		this.inventoryService = inventoryService;
	}

	@GetMapping("/{productId}")
	public InventoryResponse get(@PathVariable Long productId) {
		return new InventoryResponse(productId, inventoryService.getQuantityOnHand(productId));
	}

	@PostMapping("/{productId}/adjustments")
	public InventoryResponse adjust(@PathVariable Long productId,
			@Valid @RequestBody InventoryAdjustmentRequest request, @AuthenticationPrincipal Jwt jwt) {
		Long performedByUserId = Long.valueOf(jwt.getSubject());
		int quantityOnHand = inventoryService.adjust(productId, request.quantityChange(),
				request.reason().toMovementReason(), request.note(), performedByUserId);
		return new InventoryResponse(productId, quantityOnHand);
	}

	@GetMapping("/{productId}/movements")
	public PageResponse<InventoryMovementResponse> movements(@PathVariable Long productId,
			@ParameterObject @PageableDefault(size = 20, sort = { "createdAt", "id" }, direction = Sort.Direction.DESC)
			Pageable pageable) {
		return PageResponse.from(inventoryService.listMovements(productId, pageable).map(InventoryMovementResponse::from));
	}
}
