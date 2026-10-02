package com.medbridge.dto;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

/** The single error shape every endpoint returns, built by GlobalExceptionHandler. */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path) {

    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(LocalDateTime.now(), status.value(), status.getReasonPhrase(), message, path);
    }
}
