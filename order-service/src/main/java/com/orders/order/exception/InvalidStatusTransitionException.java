package com.orders.order.exception;

import com.orders.order.entity.OrderStatus;

public class InvalidStatusTransitionException extends RuntimeException {

  public InvalidStatusTransitionException(OrderStatus from, OrderStatus to) {
    super("Invalid status transition from " + from + " to " + to);
  }
}
