package com.orders.order.dto;

import com.orders.order.entity.OrderStatus;
import java.math.BigDecimal;

public record OrderCancelledEvent(Long orderId, BigDecimal totalPrice, OrderStatus status) {
}
