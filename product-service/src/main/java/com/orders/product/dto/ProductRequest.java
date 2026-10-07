package com.orders.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProductRequest {

  @NotBlank(message = "name must not be blank")
  private String name;

  private String description;

  @NotNull(message = "price must not be null")
  @DecimalMin(value = "0.01", inclusive = true, message = "price must be positive")
  private BigDecimal price;

  @NotNull(message = "stock must not be null")
  @Min(value = 0, message = "stock must be >= 0")
  private Integer stock;
}
