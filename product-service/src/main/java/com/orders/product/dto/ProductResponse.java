package com.orders.product.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

  private Long id;
  private String name;
  private String description;
  private BigDecimal price;
  private Integer stock;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
