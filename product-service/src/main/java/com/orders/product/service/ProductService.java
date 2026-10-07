package com.orders.product.service;

import com.orders.product.dto.ProductRequest;
import com.orders.product.dto.ProductResponse;
import com.orders.product.entity.Product;
import com.orders.product.exception.InsufficientStockException;
import com.orders.product.exception.ProductNotFoundException;
import com.orders.product.repository.ProductRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

  private final ProductRepository repository;

  @Transactional
  public ProductResponse create(ProductRequest request) {
    Product product = new Product();
    product.setName(request.getName());
    product.setDescription(request.getDescription());
    product.setPrice(request.getPrice());
    product.setStock(request.getStock());
    return toResponse(repository.save(product));
  }

  @Transactional(readOnly = true)
  public List<ProductResponse> findAll() {
    return repository.findAll().stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public ProductResponse findById(Long id) {
    return toResponse(getOrThrow(id));
  }

  @Transactional
  public ProductResponse update(Long id, ProductRequest request) {
    Product product = getOrThrow(id);
    product.setName(request.getName());
    product.setDescription(request.getDescription());
    product.setPrice(request.getPrice());
    product.setStock(request.getStock());
    return toResponse(repository.save(product));
  }

  @Transactional
  public void delete(Long id) {
    Product product = getOrThrow(id);
    repository.delete(product);
  }

  @Transactional
  public ProductResponse changeStock(Long id, int quantity) {
    Product product = getOrThrow(id);
    int newStock = product.getStock() + quantity;
    if (newStock < 0) {
      throw new InsufficientStockException(id, quantity, product.getStock());
    }
    product.setStock(newStock);
    return toResponse(repository.save(product));
  }

  private Product getOrThrow(Long id) {
    return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
  }

  private ProductResponse toResponse(Product p) {
    return new ProductResponse(
        p.getId(), p.getName(), p.getDescription(), p.getPrice(),
        p.getStock(), p.getCreatedAt(), p.getUpdatedAt());
  }
}
