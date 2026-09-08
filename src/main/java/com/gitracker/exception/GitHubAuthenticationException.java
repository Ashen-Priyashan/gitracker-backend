package com.gitracker.exception;

public class GitHubAuthenticationException extends RuntimeException {

    public GitHubAuthenticationException(String message) {
        super(message);
    }
}
