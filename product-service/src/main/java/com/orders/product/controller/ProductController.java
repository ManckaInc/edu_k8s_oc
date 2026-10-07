package com.orders.product.controller;

import com.orders.product.dto.ProductRequest;
import com.orders.product.dto.ProductResponse;
import com.orders.product.dto.StockChangeRequest;
import com.orders.product.service.ProductService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

  private final ProductService service;

  @PostMapping
  public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
  }

  @GetMapping
  public List<ProductResponse> findAll() {
    return service.findAll();
  }

  @GetMapping("/{id}")
  public ProductResponse findById(@PathVariable Long id) {
    return service.findById(id);
  }

  @PutMapping("/{id}")
  public ProductResponse update(@PathVariable Long id,
      @Valid @RequestBody ProductRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{id}/stock")
  public ProductResponse changeStock(@PathVariable Long id,
      @Valid @RequestBody StockChangeRequest request) {
    return service.changeStock(id, request.getQuantity());
  }
}
