package com.observability.metrics.demo.controller;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * Centralised exception handler that returns structured error responses
 * and increments a custom error counter for each exception class.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final MeterRegistry meterRegistry;

    public GlobalExceptionHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleBadRequest(IllegalArgumentException ex) {
        incrementErrorCounter("IllegalArgumentException", "400");
        log.warn("Bad request: {}", ex.getMessage());
        return errorBody(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleRuntimeException(RuntimeException ex) {
        incrementErrorCounter(ex.getClass().getSimpleName(), "500");
        log.error("Unhandled runtime exception", ex);
        return errorBody(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGenericException(Exception ex) {
        incrementErrorCounter(ex.getClass().getSimpleName(), "500");
        log.error("Unhandled exception", ex);
        return errorBody(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private void incrementErrorCounter(String exceptionType, String statusCode) {
        Counter.builder("demo.errors")
                .description("Application error counter")
                .tag("exception", exceptionType)
                .tag("status", statusCode)
                .register(meterRegistry)
                .increment();
    }

    private Map<String, Object> errorBody(HttpStatus status, String message) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message
        );
    }
}

