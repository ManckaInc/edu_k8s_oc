package com.orders.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.order.dto.ChangeStatusRequest;
import com.orders.order.dto.CreateOrderRequest;
import com.orders.order.dto.OrderItemRequest;
import com.orders.order.dto.OrderItemResponse;
import com.orders.order.dto.OrderResponse;
import com.orders.order.entity.OrderStatus;
import com.orders.order.service.OrderService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = com.orders.order.controller.OrderController.class)
class OrderControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private OrderService orderService;

  @Test
  void createOrderReturns201() throws Exception {
    OrderItemRequest item = new OrderItemRequest();
    item.setProductId(1L);
    item.setQuantity(2);
    CreateOrderRequest request = new CreateOrderRequest();
    request.setItems(List.of(item));

    OrderItemResponse itemResponse =
        new OrderItemResponse(1L, "Laptop", 2,
            new BigDecimal("3500.00"), new BigDecimal("7000.00"));
    OrderResponse response = new OrderResponse(1L, OrderStatus.NEW,
        new BigDecimal("7000.00"), List.of(itemResponse), null, null);
    when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(response);

    mockMvc.perform(post("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.totalPrice").value(7000.00));
  }

  @Test
  void createOrderValidationFailsOnEmptyItems() throws Exception {
    mockMvc.perform(post("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"items\":[]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getOrderById() throws Exception {
    OrderResponse response = new OrderResponse(1L, OrderStatus.NEW,
        new BigDecimal("100.00"), List.of(), null, null);
    when(orderService.findById(1L)).thenReturn(response);

    mockMvc.perform(get("/api/orders/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("NEW"));
  }

  @Test
  void changeStatus() throws Exception {
    OrderResponse response = new OrderResponse(1L, OrderStatus.CONFIRMED,
        new BigDecimal("100.00"), List.of(), null, null);
    when(orderService.changeStatus(eq(1L), any(ChangeStatusRequest.class)))
        .thenReturn(response);

    mockMvc.perform(patch("/api/orders/1/status")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"CONFIRMED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
  }

  @Test
  void cancelOrder() throws Exception {
    OrderResponse response = new OrderResponse(1L, OrderStatus.CANCELLED,
        new BigDecimal("100.00"), List.of(), null, null);
    when(orderService.cancelOrder(1L)).thenReturn(response);

    mockMvc.perform(post("/api/orders/1/cancel"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }
}
