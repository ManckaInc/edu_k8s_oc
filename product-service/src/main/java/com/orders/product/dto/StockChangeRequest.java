package com.orders.product.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StockChangeRequest {

  @NotNull(message = "quantity must not be null")
  private Integer quantity;
}
