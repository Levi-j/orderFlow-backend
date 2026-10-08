package io.github.levij.orderflow.order;

import java.net.URI;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.github.levij.orderflow.common.config.OpenApiConfig;
import io.github.levij.orderflow.common.web.PageResponse;
import io.github.levij.orderflow.order.dto.CreateOrderRequest;
import io.github.levij.orderflow.order.dto.OrderResponse;
import io.github.levij.orderflow.order.dto.OrderSummaryResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Orders", description = "Place, view and cancel your own orders. Requires a token for a user with the CUSTOMER role.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

	private final OrderService orderService;

	public OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@PostMapping
	public ResponseEntity<OrderResponse> place(@Valid @RequestBody CreateOrderRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		CustomerOrder order = orderService.placeOrder(customerId(jwt), request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(order.getId())
				.toUri();
		return ResponseEntity.created(location).body(OrderResponse.from(order));
	}

	@GetMapping
	public PageResponse<OrderSummaryResponse> list(
			@ParameterObject @PageableDefault(size = 20, sort = { "createdAt", "id" }, direction = Sort.Direction.DESC)
			Pageable pageable, @AuthenticationPrincipal Jwt jwt) {
		return PageResponse.from(orderService.listForCustomer(customerId(jwt), pageable).map(OrderSummaryResponse::from));
	}

	@GetMapping("/{id}")
	public OrderResponse get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
		return OrderResponse.from(orderService.getForCustomer(customerId(jwt), id));
	}

	@PostMapping("/{id}/cancel")
	public OrderResponse cancel(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
		return OrderResponse.from(orderService.cancelForCustomer(customerId(jwt), id));
	}

	private static Long customerId(Jwt jwt) {
		return Long.valueOf(jwt.getSubject());
	}
}
