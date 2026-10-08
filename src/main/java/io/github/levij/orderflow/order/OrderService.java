package io.github.levij.orderflow.order;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.levij.orderflow.common.error.ConflictException;
import io.github.levij.orderflow.common.error.ErrorCode;
import io.github.levij.orderflow.common.error.NotFoundException;
import io.github.levij.orderflow.inventory.InventoryService;
import io.github.levij.orderflow.order.dto.CreateOrderRequest;
import io.github.levij.orderflow.order.dto.OrderItemRequest;
import io.github.levij.orderflow.product.Product;
import io.github.levij.orderflow.product.ProductService;

@Service
public class OrderService {

	private static final String IDEMPOTENCY_KEY_UNIQUE_CONSTRAINT = "uk_orders_customer_idempotency_key";

	private final OrderRepository orderRepository;
	private final ProductService productService;
	private final InventoryService inventoryService;

	public OrderService(OrderRepository orderRepository, ProductService productService,
			InventoryService inventoryService) {
		this.orderRepository = orderRepository;
		this.productService = productService;
		this.inventoryService = inventoryService;
	}

	@Transactional
	public OrderPlacement placeOrder(Long customerId, String idempotencyKey, CreateOrderRequest request) {
		String requestHash = RequestFingerprint.of(request.items());

		Optional<CustomerOrder> existing =
				orderRepository.findWithItemsByCustomerIdAndIdempotencyKey(customerId, idempotencyKey);
		if (existing.isPresent()) {
			return replay(existing.get(), requestHash);
		}

		List<OrderItemRequest> lines = request.items().stream()
				.sorted(Comparator.comparing(OrderItemRequest::productId))
				.toList();
		Map<Long, Product> products = loadOrderableProducts(lines);

		List<OrderItem> items = new ArrayList<>();
		for (OrderItemRequest line : lines) {
			Product product = products.get(line.productId());
			items.add(OrderItem.create(product.getId(), product.getSku(), product.getName(), product.getPrice(),
					line.quantity()));
		}

		Long orderId = insert(CustomerOrder.place(customerId, idempotencyKey, requestHash, items));

		for (OrderItemRequest line : lines) {
			inventoryService.decreaseForOrder(line.productId(), line.quantity(), orderId, customerId);
		}

		return OrderPlacement.created(findForCustomer(customerId, orderId));
	}

	@Transactional(readOnly = true)
	public Page<CustomerOrder> listForCustomer(Long customerId, Pageable pageable) {
		return orderRepository.findByCustomerId(customerId, pageable);
	}

	@Transactional(readOnly = true)
	public CustomerOrder getForCustomer(Long customerId, Long orderId) {
		return findForCustomer(customerId, orderId);
	}

	@Transactional
	public CustomerOrder cancelForCustomer(Long customerId, Long orderId) {
		return cancel(findForCustomer(customerId, orderId), customerId);
	}

	@Transactional(readOnly = true)
	public Page<CustomerOrder> listForAdmin(OrderStatus status, Pageable pageable) {
		if (status == null) {
			return orderRepository.findAll(pageable);
		}
		return orderRepository.findByStatus(status, pageable);
	}

	@Transactional(readOnly = true)
	public CustomerOrder getForAdmin(Long orderId) {
		return findWithItems(orderId);
	}

	@Transactional
	public CustomerOrder confirm(Long orderId) {
		CustomerOrder order = findWithItems(orderId);
		order.confirm();
		return order;
	}

	@Transactional
	public CustomerOrder cancelForAdmin(Long orderId, Long adminId) {
		return cancel(findWithItems(orderId), adminId);
	}

	private CustomerOrder cancel(CustomerOrder order, Long performedByUserId) {
		order.cancel();
		orderRepository.flush();

		List<OrderItem> items = order.getItems().stream()
				.sorted(Comparator.comparing(OrderItem::getProductId))
				.toList();
		for (OrderItem item : items) {
			inventoryService.restoreForCancelledOrder(item.getProductId(), item.getQuantity(), order.getId(),
					performedByUserId);
		}

		return findWithItems(order.getId());
	}

	private static OrderPlacement replay(CustomerOrder existing, String requestHash) {
		if (!existing.getRequestHash().equals(requestHash)) {
			throw new ConflictException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
					"The idempotency key was already used for a different order request.");
		}
		return OrderPlacement.replay(existing);
	}

	private Long insert(CustomerOrder order) {
		try {
			return orderRepository.saveAndFlush(order).getId();
		}
		catch (DataIntegrityViolationException ex) {
			if (violatesIdempotencyKeyConstraint(ex)) {
				throw new ConflictException(ErrorCode.IDEMPOTENCY_KEY_IN_USE,
						"Another request with this idempotency key was being processed at the same time. "
								+ "Retry the request.");
			}
			throw ex;
		}
	}

	private static boolean violatesIdempotencyKeyConstraint(DataIntegrityViolationException ex) {
		for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation
					&& IDEMPOTENCY_KEY_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName())) {
				return true;
			}
		}
		return false;
	}

	private Map<Long, Product> loadOrderableProducts(List<OrderItemRequest> lines) {
		List<Long> productIds = lines.stream().map(OrderItemRequest::productId).toList();
		Map<Long, Product> products = productService.findActiveByIds(productIds).stream()
				.collect(Collectors.toMap(Product::getId, Function.identity()));

		for (Long productId : productIds) {
			if (!products.containsKey(productId)) {
				throw new ConflictException(ErrorCode.PRODUCT_NOT_AVAILABLE,
						"Product " + productId + " is not available for ordering.");
			}
		}
		return products;
	}

	private CustomerOrder findForCustomer(Long customerId, Long orderId) {
		return orderRepository.findWithItemsByIdAndCustomerId(orderId, customerId)
				.orElseThrow(() -> orderNotFound(orderId));
	}

	private CustomerOrder findWithItems(Long orderId) {
		return orderRepository.findWithItemsById(orderId)
				.orElseThrow(() -> orderNotFound(orderId));
	}

	private static NotFoundException orderNotFound(Long orderId) {
		return new NotFoundException("Order " + orderId + " not found");
	}
}
