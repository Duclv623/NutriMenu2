package com.example.mealoptimizerservice.exception;

import org.springframework.http.HttpStatus;

public class OptimizerBusinessException extends RuntimeException {
    private final HttpStatus status;
    public OptimizerBusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
    public HttpStatus getStatus() { return status; }
}
