package com.gitracker.exception;

public class GitHubServiceUnavailableException extends RuntimeException {

    public GitHubServiceUnavailableException(String message) {
        super(message);
    }

    public GitHubServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
