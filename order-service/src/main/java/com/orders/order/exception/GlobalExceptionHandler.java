package com.orders.order.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(OrderNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNotFound(
      OrderNotFoundException ex, HttpServletRequest request) {
    return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request);
  }

  @ExceptionHandler(ProductNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleProductNotFound(
      ProductNotFoundException ex, HttpServletRequest request) {
    return error(HttpStatus.BAD_REQUEST, "PRODUCT_NOT_FOUND", ex.getMessage(), request);
  }

  @ExceptionHandler(InsufficientStockException.class)
  public ResponseEntity<Map<String, Object>> handleInsufficientStock(
      InsufficientStockException ex, HttpServletRequest request) {
    return error(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK", ex.getMessage(), request);
  }

  @ExceptionHandler(InvalidStatusTransitionException.class)
  public ResponseEntity<Map<String, Object>> handleStatusTransition(
      InvalidStatusTransitionException ex, HttpServletRequest request) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION", ex.getMessage(), request);
  }

  @ExceptionHandler(ProductServiceException.class)
  public ResponseEntity<Map<String, Object>> handleProductService(
      ProductServiceException ex, HttpServletRequest request) {
    return error(HttpStatus.BAD_GATEWAY, "PRODUCT_SERVICE_ERROR", ex.getMessage(), request);
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
      IllegalArgumentException.class})
  public ResponseEntity<Map<String, Object>> handleBadRequest(
      Exception ex, HttpServletRequest request) {
    String message;
    if (ex instanceof MethodArgumentNotValidException validation) {
      message = validation.getBindingResult().getFieldErrors().stream()
          .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
          .collect(Collectors.joining("; "));
      return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
    }
    message = ex.getMessage();
    return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGeneric(
      Exception ex, HttpServletRequest request) {
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
        "Unexpected error: " + ex.getMessage(), request);
  }

  private ResponseEntity<Map<String, Object>> error(
      HttpStatus status, String error, String message, HttpServletRequest request) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", LocalDateTime.now().toString());
    body.put("status", status.value());
    body.put("error", error);
    body.put("message", message);
    body.put("path", request.getRequestURI());
    return ResponseEntity.status(status).body(body);
  }
}
