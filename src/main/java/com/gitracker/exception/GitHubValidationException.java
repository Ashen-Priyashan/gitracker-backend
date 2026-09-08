package com.gitracker.exception;

public class GitHubValidationException extends RuntimeException {

    public GitHubValidationException(String message) {
        super(message);
    }
}
