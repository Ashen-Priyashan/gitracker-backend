package com.gitracker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String message,
        int status,
        LocalDateTime timestamp,
        Long retryAfterSeconds,
        Long resetTimeSeconds
) {
    public ErrorResponse(String message, int status, LocalDateTime timestamp) {
        this(message, status, timestamp, null, null);
    }
}