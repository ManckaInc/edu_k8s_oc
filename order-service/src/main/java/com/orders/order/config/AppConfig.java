package com.orders.order.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

  @Value("${app.rabbitmq.exchange:orders.exchange}")
  private String exchangeName;

  @Value("${app.rabbitmq.queues.created:orders.created.queue}")
  private String createdQueue;

  @Value("${app.rabbitmq.queues.cancelled:orders.cancelled.queue}")
  private String cancelledQueue;

  @Value("${app.rabbitmq.routing.created:order.created}")
  private String createdRoutingKey;

  @Value("${app.rabbitmq.routing.cancelled:order.cancelled}")
  private String cancelledRoutingKey;

  @Value("${app.product-service.base-url:http://localhost:8081}")
  private String productServiceBaseUrl;

  @Bean
  public DirectExchange ordersExchange() {
    return new DirectExchange(exchangeName, true, false);
  }

  @Bean
  public Queue createdQueue() {
    return QueueBuilder.durable(createdQueue).build();
  }

  @Bean
  public Queue cancelledQueue() {
    return QueueBuilder.durable(cancelledQueue).build();
  }

  @Bean
  public Binding createdBinding(DirectExchange ordersExchange, Queue createdQueue) {
    return BindingBuilder.bind(createdQueue).to(ordersExchange).with(createdRoutingKey);
  }

  @Bean
  public Binding cancelledBinding(DirectExchange ordersExchange, Queue cancelledQueue) {
    return BindingBuilder.bind(cancelledQueue).to(ordersExchange).with(cancelledRoutingKey);
  }

  @Bean
  public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
    Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
    DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
    typeMapper.setTrustedPackages("com.orders.order.dto");
    converter.setJavaTypeMapper(typeMapper);
    return converter;
  }

  @Bean
  public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
      Jackson2JsonMessageConverter converter) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(converter);
    return template;
  }

  @Bean
  public RestClient productServiceRestClient(RestClient.Builder builder) {
    return builder.baseUrl(productServiceBaseUrl)
        .requestFactory(new HttpComponentsClientHttpRequestFactory())
        .build();
  }
}
