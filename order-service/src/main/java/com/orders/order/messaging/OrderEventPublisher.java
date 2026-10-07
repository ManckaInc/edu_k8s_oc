package com.orders.order.messaging;

import com.orders.order.dto.OrderCancelledEvent;
import com.orders.order.dto.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

  private final RabbitTemplate rabbitTemplate;

  @Value("${app.rabbitmq.exchange:orders.exchange}")
  private String exchange;

  @Value("${app.rabbitmq.routing.created:order.created}")
  private String createdRoutingKey;

  @Value("${app.rabbitmq.routing.cancelled:order.cancelled}")
  private String cancelledRoutingKey;

  public void publishCreated(OrderCreatedEvent event) {
    log.info("Publishing OrderCreatedEvent: orderId={}", event.orderId());
    rabbitTemplate.convertAndSend(exchange, createdRoutingKey, event);
  }

  public void publishCancelled(OrderCancelledEvent event) {
    log.info("Publishing OrderCancelledEvent: orderId={}", event.orderId());
    rabbitTemplate.convertAndSend(exchange, cancelledRoutingKey, event);
  }
}
