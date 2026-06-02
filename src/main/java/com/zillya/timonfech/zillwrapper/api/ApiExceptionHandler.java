package com.zillya.timonfech.zillwrapper.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestControllerAdvice(basePackages = "com.zillya.timonfech.zillwrapper.api")
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApi(ApiException ex) {
        ApiErrorResponse body = new ApiErrorResponse(
                ex.getCode().name(),
                ex.getMessage(),
                List.of(),
                Instant.now().toString()
        );
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled API error: {}", ex.getMessage(), ex);
        ApiErrorResponse body = new ApiErrorResponse(
                ApiErrorCode.INTERNAL_ERROR.name(),
                "Internal server error",
                List.of(),
                Instant.now().toString()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    public record ApiErrorResponse(String code, String message, List<String> details, String timestamp) {
    }
}
