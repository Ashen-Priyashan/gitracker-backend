package com.gitracker.exception;

public class RateLimitExceededException extends RuntimeException {

    private final Long retryAfterSeconds;
    private final Long resetTimeSeconds;

    public RateLimitExceededException(String message) {
        this(message, null, null);
    }

    public RateLimitExceededException(String message, Long retryAfterSeconds, Long resetTimeSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
        this.resetTimeSeconds = resetTimeSeconds;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public Long getResetTimeSeconds() {
        return resetTimeSeconds;
    }
}