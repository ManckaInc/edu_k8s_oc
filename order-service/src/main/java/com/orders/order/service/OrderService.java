package com.orders.order.service;

import com.orders.order.client.ProductServiceClient;
import com.orders.order.dto.ChangeStatusRequest;
import com.orders.order.dto.CreateOrderRequest;
import com.orders.order.dto.OrderCancelledEvent;
import com.orders.order.dto.OrderCreatedEvent;
import com.orders.order.dto.OrderItemRequest;
import com.orders.order.dto.OrderItemResponse;
import com.orders.order.dto.OrderResponse;
import com.orders.order.dto.ProductDto;
import com.orders.order.entity.OrderItem;
import com.orders.order.entity.OrderStatus;
import com.orders.order.entity.ShopOrder;
import com.orders.order.exception.InsufficientStockException;
import com.orders.order.exception.InvalidStatusTransitionException;
import com.orders.order.exception.OrderNotFoundException;
import com.orders.order.messaging.OrderEventPublisher;
import com.orders.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

  private final OrderRepository orderRepository;
  private final ProductServiceClient productClient;
  private final OrderEventPublisher eventPublisher;

  @Transactional
  public OrderResponse createOrder(CreateOrderRequest request) {
    ShopOrder order = new ShopOrder();
    order.setStatus(OrderStatus.NEW);

    BigDecimal total = BigDecimal.ZERO;
    List<ReservedStock> reserved = new ArrayList<>();

    try {
      for (OrderItemRequest item : request.getItems()) {
        ProductDto product = productClient.getProduct(item.getProductId());

        if (product.getStock() < item.getQuantity()) {
          throw new InsufficientStockException("Insufficient stock for product "
              + product.getId() + ": requested " + item.getQuantity()
              + ", available " + product.getStock());
        }

        BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        total = total.add(subtotal);

        OrderItem orderItem = new OrderItem();
        orderItem.setProductId(product.getId());
        orderItem.setProductName(product.getName());
        orderItem.setQuantity(item.getQuantity());
        orderItem.setPrice(product.getPrice());
        orderItem.setSubtotal(subtotal);
        order.addItem(orderItem);
      }

      order.setTotalPrice(total);
      ShopOrder saved = orderRepository.save(order);

      for (OrderItem orderItem : saved.getItems()) {
        productClient.changeStock(orderItem.getProductId(), -orderItem.getQuantity());
        reserved.add(new ReservedStock(orderItem.getProductId(), orderItem.getQuantity()));
      }

      eventPublisher.publishCreated(
          new OrderCreatedEvent(saved.getId(), saved.getTotalPrice(), saved.getStatus()));

      return toResponse(saved);
    } catch (RuntimeException e) {
      for (ReservedStock r : reserved) {
        try {
          productClient.changeStock(r.productId(), r.quantity());
        } catch (Exception ignored) {
          // kompensacja best-effort; pierwotny wyjątek jest rzucany ponownie poniżej
        }
      }
      throw e;
    }
  }

  @Transactional(readOnly = true)
  public List<OrderResponse> findAll() {
    return orderRepository.findAll().stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public OrderResponse findById(Long id) {
    return toResponse(getOrThrow(id));
  }

  @Transactional
  public OrderResponse changeStatus(Long id, ChangeStatusRequest request) {
    ShopOrder order = getOrThrow(id);
    OrderStatus from = order.getStatus();
    OrderStatus to = request.getStatus();
    if (to == null) {
      throw new IllegalArgumentException("status must not be null");
    }
    if (!isTransitionAllowed(from, to)) {
      throw new InvalidStatusTransitionException(from, to);
    }
    if (to == OrderStatus.CANCELLED) {
      return cancelOrder(id);
    }
    order.setStatus(to);
    return toResponse(orderRepository.save(order));
  }

  @Transactional
  public OrderResponse cancelOrder(Long id) {
    ShopOrder order = getOrThrow(id);
    if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.CONFIRMED) {
      throw new InvalidStatusTransitionException(order.getStatus(), OrderStatus.CANCELLED);
    }
    for (OrderItem item : order.getItems()) {
      productClient.changeStock(item.getProductId(), item.getQuantity());
    }
    order.setStatus(OrderStatus.CANCELLED);
    ShopOrder saved = orderRepository.save(order);
    eventPublisher.publishCancelled(
        new OrderCancelledEvent(saved.getId(), saved.getTotalPrice(), saved.getStatus()));
    return toResponse(saved);
  }

  static boolean isTransitionAllowed(OrderStatus from, OrderStatus to) {
    return switch (from) {
      case NEW -> to == OrderStatus.CONFIRMED || to == OrderStatus.CANCELLED;
      case CONFIRMED -> to == OrderStatus.COMPLETED || to == OrderStatus.CANCELLED;
      default -> false;
    };
  }

  private ShopOrder getOrThrow(Long id) {
    return orderRepository.findById(id)
        .orElseThrow(() -> new OrderNotFoundException(id));
  }

  private OrderResponse toResponse(ShopOrder order) {
    List<OrderItemResponse> items = order.getItems().stream()
        .map(i -> new OrderItemResponse(
            i.getProductId(), i.getProductName(), i.getQuantity(), i.getPrice(), i.getSubtotal()))
        .toList();
    return new OrderResponse(order.getId(), order.getStatus(), order.getTotalPrice(),
        items, order.getCreatedAt(), order.getUpdatedAt());
  }

  private record ReservedStock(Long productId, int quantity) {
  }
}
