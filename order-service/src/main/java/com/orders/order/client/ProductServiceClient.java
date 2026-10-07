package com.orders.order.client;

import com.orders.order.dto.ProductDto;
import com.orders.order.exception.InsufficientStockException;
import com.orders.order.exception.ProductNotFoundException;
import com.orders.order.exception.ProductServiceException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductServiceClient {

  private final RestClient productServiceRestClient;

  public ProductDto getProduct(Long productId) {
    try {
      ProductDto product = productServiceRestClient.get()
          .uri("/api/products/{id}", productId)
          .retrieve()
          .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
            throw new ProductNotFoundException(productId);
          })
          .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
            throw new ProductServiceException(
                "product-service unavailable, status=" + res.getStatusCode());
          })
          .body(ProductDto.class);
      if (product == null) {
        throw new ProductServiceException("Empty response for product " + productId);
      }
      return product;
    } catch (ProductNotFoundException | ProductServiceException e) {
      throw e;
    } catch (Exception e) {
      throw new ProductServiceException(
          "Failed to fetch product " + productId + ": " + e.getMessage(), e);
    }
  }

  public void changeStock(Long productId, int quantity) {
    try {
      productServiceRestClient.patch()
          .uri("/api/products/{id}/stock", productId)
          .body(Map.of("quantity", quantity))
          .retrieve()
          .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
            if (res.getStatusCode().value() == 404) {
              throw new ProductNotFoundException(productId);
            }
            throw new InsufficientStockException(
                "Insufficient stock for product " + productId);
          })
          .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
            throw new ProductServiceException(
                "product-service unavailable, status=" + res.getStatusCode());
          })
          .toBodilessEntity();
    } catch (InsufficientStockException | ProductNotFoundException | ProductServiceException e) {
      throw e;
    } catch (Exception e) {
      throw new ProductServiceException(
          "Failed to change stock for product " + productId + ": " + e.getMessage(), e);
    }
  }
}
