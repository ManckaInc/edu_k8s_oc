package com.orders.product;

import com.orders.product.dto.ProductRequest;
import com.orders.product.dto.ProductResponse;
import com.orders.product.entity.Product;
import com.orders.product.exception.InsufficientStockException;
import com.orders.product.exception.ProductNotFoundException;
import com.orders.product.repository.ProductRepository;
import com.orders.product.service.ProductService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  @Mock
  private ProductRepository repository;

  @InjectMocks
  private ProductService service;

  @Test
  void createProduct() {
    ProductRequest request = new ProductRequest();
    request.setName("Laptop");
    request.setDescription("Business laptop");
    request.setPrice(new BigDecimal("3500.00"));
    request.setStock(10);

    Product saved = new Product();
    saved.setId(1L);
    saved.setName("Laptop");
    saved.setDescription("Business laptop");
    saved.setPrice(new BigDecimal("3500.00"));
    saved.setStock(10);

    when(repository.save(any(Product.class))).thenReturn(saved);

    ProductResponse response = service.create(request);

    assertEquals(1L, response.getId());
    assertEquals("Laptop", response.getName());
    assertEquals(new BigDecimal("3500.00"), response.getPrice());
    assertEquals(10, response.getStock());
    verify(repository).save(any(Product.class));
  }

  @Test
  void getProduct() {
    Product product = new Product();
    product.setId(1L);
    product.setName("Laptop");
    product.setPrice(new BigDecimal("3500.00"));
    product.setStock(10);

    when(repository.findById(1L)).thenReturn(Optional.of(product));

    ProductResponse response = service.findById(1L);

    assertEquals("Laptop", response.getName());
  }

  @Test
  void getProductNotFound() {
    when(repository.findById(10L)).thenReturn(Optional.empty());

    assertThrows(ProductNotFoundException.class, () -> service.findById(10L));
  }

  @Test
  void updateStockDecrease() {
    Product product = new Product();
    product.setId(1L);
    product.setName("Laptop");
    product.setPrice(new BigDecimal("3500.00"));
    product.setStock(10);

    when(repository.findById(1L)).thenReturn(Optional.of(product));
    when(repository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    ProductResponse response = service.changeStock(1L, -2);

    assertEquals(8, response.getStock());
  }

  @Test
  void updateStockInsufficient() {
    Product product = new Product();
    product.setId(1L);
    product.setName("Laptop");
    product.setPrice(new BigDecimal("3500.00"));
    product.setStock(1);

    when(repository.findById(1L)).thenReturn(Optional.of(product));

    assertThrows(InsufficientStockException.class, () -> service.changeStock(1L, -5));
  }

  @Test
  void findAll() {
    Product product = new Product();
    product.setId(1L);
    product.setName("Laptop");
    product.setPrice(new BigDecimal("100.00"));
    product.setStock(5);

    when(repository.findAll()).thenReturn(List.of(product));

    List<ProductResponse> all = service.findAll();

    assertEquals(1, all.size());
    assertEquals("Laptop", all.get(0).getName());
  }
}
