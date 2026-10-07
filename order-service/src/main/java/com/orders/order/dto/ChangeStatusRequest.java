package com.orders.order.dto;

import com.orders.order.entity.OrderStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChangeStatusRequest {

  private OrderStatus status;
}
