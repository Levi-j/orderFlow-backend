package io.github.levij.orderflow.order;

import java.net.URI;
import java.util.regex.Pattern;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.github.levij.orderflow.common.config.OpenApiConfig;
import io.github.levij.orderflow.common.web.PageResponse;
import io.github.levij.orderflow.order.dto.CreateOrderRequest;
import io.github.levij.orderflow.order.dto.OrderResponse;
import io.github.levij.orderflow.order.dto.OrderSummaryResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Orders", description = "Place, view and cancel your own orders. Requires a token for a user with the CUSTOMER role.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

	static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
	static final String IDEMPOTENT_REPLAYED_HEADER = "Idempotent-Replayed";

	private static final Pattern IDEMPOTENCY_KEY_FORMAT = Pattern.compile("[A-Za-z0-9_-]{1,100}");

	private final OrderService orderService;

	public OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@PostMapping
	@ApiResponse(responseCode = "201", description = "The order was placed, or an earlier order placed with the same "
			+ "Idempotency-Key and the same items was returned again.",
			headers = @Header(name = IDEMPOTENT_REPLAYED_HEADER, description = "Present with the value true when the "
					+ "response repeats an earlier order instead of creating a new one.",
					schema = @Schema(type = "string", allowableValues = "true")))
	public ResponseEntity<OrderResponse> place(
			@Parameter(description = "Key chosen by the client that makes the request safe to retry. Sending the same "
					+ "key with the same items returns the original order instead of placing a second one. 1-100 "
					+ "characters: letters, digits, '_' or '-'. Case-sensitive and scoped to the customer.",
					schema = @Schema(minLength = 1, maxLength = 100, pattern = "^[A-Za-z0-9_-]{1,100}$"))
			@RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
			@Valid @RequestBody CreateOrderRequest request, @AuthenticationPrincipal Jwt jwt) {
		if (!IDEMPOTENCY_KEY_FORMAT.matcher(idempotencyKey).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The " + IDEMPOTENCY_KEY_HEADER
					+ " header must be 1 to 100 characters long and contain only letters, digits, '_' or '-'.");
		}

		OrderPlacement placement = orderService.placeOrder(customerId(jwt), idempotencyKey, request);
		CustomerOrder order = placement.order();
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(order.getId())
				.toUri();

		ResponseEntity.BodyBuilder response = ResponseEntity.created(location);
		if (placement.replayed()) {
			response.header(IDEMPOTENT_REPLAYED_HEADER, "true");
		}
		return response.body(OrderResponse.asPlaced(order));
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
