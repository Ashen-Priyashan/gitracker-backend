package com.gitracker.exception;

import com.gitracker.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.LocalDateTime;


@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
public ResponseEntity<ErrorResponse> handleRateLimitExceeded(
        RateLimitExceededException exception
) {

    ErrorResponse errorResponse =
            new ErrorResponse(
                    exception.getMessage(),
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    LocalDateTime.now()
            );

    return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .body(errorResponse);
}

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException exception
    ) {

        ErrorResponse errorResponse =
                new ErrorResponse(
                        exception.getMessage(),
                        HttpStatus.NOT_FOUND.value(),
                        LocalDateTime.now()
                );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorResponse);
    }
}