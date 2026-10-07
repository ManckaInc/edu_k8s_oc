package com.orders.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.product.dto.ProductRequest;
import com.orders.product.dto.ProductResponse;
import com.orders.product.service.ProductService;
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

@WebMvcTest(controllers = com.orders.product.controller.ProductController.class)
class ProductControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private ProductService service;

  @Test
  void createProductReturns201() throws Exception {
    ProductRequest request = new ProductRequest();
    request.setName("Laptop");
    request.setDescription("Business laptop");
    request.setPrice(new BigDecimal("3500.00"));
    request.setStock(10);

    ProductResponse response =
        new ProductResponse(1L, "Laptop", "Business laptop",
            new BigDecimal("3500.00"), 10, null, null);
    when(service.create(any(ProductRequest.class))).thenReturn(response);

    mockMvc.perform(post("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.name").value("Laptop"));
  }

  @Test
  void createProductValidationFails() throws Exception {
    mockMvc.perform(post("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"\",\"price\":-5,\"stock\":-1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
  }

  @Test
  void getAllProducts() throws Exception {
    ProductResponse response =
        new ProductResponse(1L, "Laptop", "Business laptop",
            new BigDecimal("3500.00"), 10, null, null);
    when(service.findAll()).thenReturn(List.of(response));

    mockMvc.perform(get("/api/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Laptop"));
  }

  @Test
  void changeStock() throws Exception {
    ProductResponse response =
        new ProductResponse(1L, "Laptop", "Business laptop",
            new BigDecimal("3500.00"), 8, null, null);
    when(service.changeStock(eq(1L), eq(-2))).thenReturn(response);

    mockMvc.perform(patch("/api/products/1/stock")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"quantity\":-2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stock").value(8));
  }
}
