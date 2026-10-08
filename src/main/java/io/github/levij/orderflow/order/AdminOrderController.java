package io.github.levij.orderflow.order;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.levij.orderflow.common.config.OpenApiConfig;
import io.github.levij.orderflow.common.web.PageResponse;
import io.github.levij.orderflow.order.dto.AdminOrderSummaryResponse;
import io.github.levij.orderflow.order.dto.OrderResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Orders", description = "View, confirm and cancel any customer's order. Requires a token for a user with the ADMIN role.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {

	private final OrderService orderService;

	public AdminOrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@GetMapping
	public PageResponse<AdminOrderSummaryResponse> list(@RequestParam(required = false) OrderStatus status,
			@ParameterObject @PageableDefault(size = 20, sort = { "createdAt", "id" }, direction = Sort.Direction.DESC)
			Pageable pageable) {
		return PageResponse.from(orderService.listForAdmin(status, pageable).map(AdminOrderSummaryResponse::from));
	}

	@GetMapping("/{id}")
	public OrderResponse get(@PathVariable Long id) {
		return OrderResponse.from(orderService.getForAdmin(id));
	}

	@PostMapping("/{id}/confirm")
	public OrderResponse confirm(@PathVariable Long id) {
		return OrderResponse.from(orderService.confirm(id));
	}

	@PostMapping("/{id}/cancel")
	public OrderResponse cancel(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
		return OrderResponse.from(orderService.cancelForAdmin(id, Long.valueOf(jwt.getSubject())));
	}
}
