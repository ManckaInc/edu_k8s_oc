package com.orders.product.exception;

public class InsufficientStockException extends RuntimeException {

  public InsufficientStockException(Long productId, int requested, int available) {
    super("Insufficient stock for product " + productId
        + ": requested change " + requested + ", available " + available);
  }

  public InsufficientStockException(String message) {
    super(message);
  }
}
