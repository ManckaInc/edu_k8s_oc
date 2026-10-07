package com.orders.order.messaging;

import com.orders.order.dto.OrderCancelledEvent;
import com.orders.order.dto.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OrderEventListener {

  @RabbitListener(queues = "${app.rabbitmq.queues.created:orders.created.queue}")
  public void onOrderCreated(OrderCreatedEvent event) {
    log.info("Received OrderCreatedEvent: orderId={}, totalPrice={}, status={}",
        event.orderId(), event.totalPrice(), event.status());
  }

  @RabbitListener(queues = "${app.rabbitmq.queues.cancelled:orders.cancelled.queue}")
  public void onOrderCancelled(OrderCancelledEvent event) {
    log.info("Received OrderCancelledEvent: orderId={}, totalPrice={}, status={}",
        event.orderId(), event.totalPrice(), event.status());
  }
}
