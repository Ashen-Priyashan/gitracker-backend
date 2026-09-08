package com.gitracker.exception;

public class GitHubForbiddenException extends RuntimeException {

    public GitHubForbiddenException(String message) {
        super(message);
    }
}
