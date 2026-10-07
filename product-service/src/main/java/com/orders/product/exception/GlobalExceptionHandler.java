package com.orders.product.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ProductNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNotFound(
      ProductNotFoundException ex, HttpServletRequest request) {
    return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request);
  }

  @ExceptionHandler(InsufficientStockException.class)
  public ResponseEntity<Map<String, Object>> handleInsufficientStock(
      InsufficientStockException ex, HttpServletRequest request) {
    return error(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK", ex.getMessage(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
        .collect(Collectors.joining("; "));
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleBadRequest(
      IllegalArgumentException ex, HttpServletRequest request) {
    return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request);
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
