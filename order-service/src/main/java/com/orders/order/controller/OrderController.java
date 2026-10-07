package com.orders.order.controller;

import com.orders.order.dto.ChangeStatusRequest;
import com.orders.order.dto.CreateOrderRequest;
import com.orders.order.dto.OrderResponse;
import com.orders.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderService orderService;

  @PostMapping
  public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request));
  }

  @GetMapping
  public List<OrderResponse> findAll() {
    return orderService.findAll();
  }

  @GetMapping("/{id}")
  public OrderResponse findById(@PathVariable Long id) {
    return orderService.findById(id);
  }

  @PatchMapping("/{id}/status")
  public OrderResponse changeStatus(@PathVariable Long id,
      @RequestBody ChangeStatusRequest request) {
    return orderService.changeStatus(id, request);
  }

  @PostMapping("/{id}/cancel")
  public OrderResponse cancel(@PathVariable Long id) {
    return orderService.cancelOrder(id);
  }
}
