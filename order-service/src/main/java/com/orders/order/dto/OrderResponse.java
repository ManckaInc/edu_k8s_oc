package com.orders.order.dto;

import com.orders.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

  private Long id;
  private OrderStatus status;
  private BigDecimal totalPrice;
  private List<OrderItemResponse> items;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
