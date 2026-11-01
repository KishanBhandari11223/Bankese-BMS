package com.bms.exception;

import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(
            ResponseStatusException exception) {
        String reason = exception.getReason();
        String message = reason == null || reason.isBlank()
                ? "The request could not be completed" : reason;
        HttpStatusCode status = exception.getStatusCode();
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}