package com.orders.order;

import com.orders.order.client.ProductServiceClient;
import com.orders.order.dto.CreateOrderRequest;
import com.orders.order.dto.OrderItemRequest;
import com.orders.order.dto.OrderResponse;
import com.orders.order.dto.ProductDto;
import com.orders.order.entity.OrderStatus;
import com.orders.order.entity.ShopOrder;
import com.orders.order.exception.InsufficientStockException;
import com.orders.order.exception.InvalidStatusTransitionException;
import com.orders.order.messaging.OrderEventPublisher;
import com.orders.order.repository.OrderRepository;
import com.orders.order.service.OrderService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock
  private OrderRepository orderRepository;

  @Mock
  private ProductServiceClient productClient;

  @Mock
  private OrderEventPublisher eventPublisher;

  @InjectMocks
  private OrderService orderService;

  private CreateOrderRequest request(Long productId, int quantity) {
    OrderItemRequest item = new OrderItemRequest();
    item.setProductId(productId);
    item.setQuantity(quantity);
    CreateOrderRequest req = new CreateOrderRequest();
    req.setItems(List.of(item));
    return req;
  }

  @Test
  void createOrderCalculatesTotalPrice() {
    ProductDto product = new ProductDto(1L, "Laptop", "Business laptop",
        new BigDecimal("3500.00"), 10);
    when(productClient.getProduct(1L)).thenReturn(product);
    when(orderRepository.save(any(ShopOrder.class)))
        .thenAnswer(inv -> {
          ShopOrder o = inv.getArgument(0);
          o.setId(1L);
          return o;
        });

    OrderResponse response = orderService.createOrder(request(1L, 2));

    assertEquals(new BigDecimal("7000.00"), response.getTotalPrice());
    assertEquals(OrderStatus.NEW, response.getStatus());
    assertEquals(1, response.getItems().size());
    assertEquals(new BigDecimal("7000.00"), response.getItems().get(0).getSubtotal());
    verify(productClient).changeStock(1L, -2);
    verify(eventPublisher).publishCreated(any());
  }

  @Test
  void createOrderInsufficientStock() {
    ProductDto product = new ProductDto(1L, "Laptop", "Business laptop",
        new BigDecimal("3500.00"), 1);
    when(productClient.getProduct(1L)).thenReturn(product);

    assertThrows(InsufficientStockException.class,
        () -> orderService.createOrder(request(1L, 5)));
    verify(orderRepository, never()).save(any());
    verify(eventPublisher, never()).publishCreated(any());
  }

  @Test
  void invalidStatusTransition() {
    ShopOrder order = new ShopOrder();
    order.setId(1L);
    order.setStatus(OrderStatus.COMPLETED);
    order.setTotalPrice(BigDecimal.TEN);
    when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

    com.orders.order.dto.ChangeStatusRequest req =
        new com.orders.order.dto.ChangeStatusRequest();
    req.setStatus(OrderStatus.CONFIRMED);

    assertThrows(InvalidStatusTransitionException.class,
        () -> orderService.changeStatus(1L, req));
  }

  @Test
  void newToConfirmedAllowed() {
    ShopOrder order = new ShopOrder();
    order.setId(1L);
    order.setStatus(OrderStatus.NEW);
    order.setTotalPrice(BigDecimal.TEN);
    when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
    when(orderRepository.save(any(ShopOrder.class))).thenAnswer(inv -> inv.getArgument(0));

    com.orders.order.dto.ChangeStatusRequest req =
        new com.orders.order.dto.ChangeStatusRequest();
    req.setStatus(OrderStatus.CONFIRMED);

    OrderResponse response = orderService.changeStatus(1L, req);

    assertEquals(OrderStatus.CONFIRMED, response.getStatus());
  }

  @Test
  void cancelOrderRestoresStockAndPublishesEvent() {
    ShopOrder order = new ShopOrder();
    order.setId(1L);
    order.setStatus(OrderStatus.NEW);
    order.setTotalPrice(new BigDecimal("7000.00"));
    com.orders.order.entity.OrderItem item = new com.orders.order.entity.OrderItem();
    item.setProductId(1L);
    item.setProductName("Laptop");
    item.setQuantity(2);
    item.setPrice(new BigDecimal("3500.00"));
    item.setSubtotal(new BigDecimal("7000.00"));
    order.addItem(item);
    when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
    when(orderRepository.save(any(ShopOrder.class))).thenAnswer(inv -> inv.getArgument(0));

    OrderResponse response = orderService.cancelOrder(1L);

    assertEquals(OrderStatus.CANCELLED, response.getStatus());
    verify(productClient).changeStock(1L, 2);
    verify(eventPublisher).publishCancelled(any());
  }

  @Test
  void cancelCompletedOrderFails() {
    ShopOrder order = new ShopOrder();
    order.setId(1L);
    order.setStatus(OrderStatus.COMPLETED);
    order.setTotalPrice(BigDecimal.TEN);
    when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

    assertThrows(InvalidStatusTransitionException.class,
        () -> orderService.cancelOrder(1L));
    verify(eventPublisher, never()).publishCancelled(any());
  }

  @Test
  void priceSnapshotKeptInOrderItem() {
    ProductDto product = new ProductDto(1L, "Laptop", "desc",
        new BigDecimal("3500.00"), 10);
    when(productClient.getProduct(1L)).thenReturn(product);

    ArgumentCaptor<ShopOrder> captor = ArgumentCaptor.forClass(ShopOrder.class);
    when(orderRepository.save(captor.capture())).thenAnswer(inv -> {
      ShopOrder o = inv.getArgument(0);
      o.setId(1L);
      return o;
    });

    orderService.createOrder(request(1L, 1));

    ShopOrder saved = captor.getValue();
    assertTrue(saved.getItems().size() == 1);
    assertEquals(new BigDecimal("3500.00"), saved.getItems().get(0).getPrice());
  }
}
