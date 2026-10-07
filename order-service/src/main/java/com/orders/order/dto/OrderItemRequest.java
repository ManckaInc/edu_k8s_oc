package com.orders.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrderItemRequest {

  @NotNull(message = "productId must not be null")
  private Long productId;

  @NotNull(message = "quantity must not be null")
  @Positive(message = "quantity must be positive")
  private Integer quantity;
}
