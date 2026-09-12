package com.gitracker.controller;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gitracker.exception.GlobalExceptionHandler;
import com.gitracker.exception.RateLimitExceededException;
import com.gitracker.exception.ResourceNotFoundException;
import com.gitracker.service.GitHubUserService;

@WebMvcTest(UserController.class)
@Import(GlobalExceptionHandler.class)
class ErrorHandlingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitHubUserService gitHubUserService;

    @Test
    void shouldReturn404WhenUserDoesNotExist() throws Exception {

        when(gitHubUserService.getUser("unknown-user"))
                .thenThrow(
                        new ResourceNotFoundException(
                                "GitHub user not found"
                        )
                );

        mockMvc.perform(get("/api/users/unknown-user")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("GitHub user not found"))
                .andExpect(jsonPath("$.status")
                        .value(404))
                .andExpect(jsonPath("$.timestamp")
                        .exists());
    }

    @Test
    void shouldReturn429WhenRateLimitExceeded() throws Exception {

        when(gitHubUserService.getUser("limited-user"))
                .thenThrow(new RateLimitExceededException("GitHub API rate limit exceeded"));

        mockMvc.perform(get("/api/users/limited-user")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message")
                        .value("GitHub API rate limit exceeded"))
                .andExpect(jsonPath("$.status")
                        .value(429))
                .andExpect(jsonPath("$.timestamp")
                        .exists());
    }

    @Test
    void shouldReturn429WithRetryMetadataWhenRateLimitExceededWithRetryAfter() throws Exception {
        when(gitHubUserService.getUser("rate-limited-user"))
                .thenThrow(new RateLimitExceededException("GitHub API rate limit exceeded. Please try again later.", 60L, 1700000000L));

        mockMvc.perform(get("/api/users/rate-limited-user")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.message").value("GitHub API rate limit exceeded. Please try again later."))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.retryAfterSeconds").value(60))
                .andExpect(jsonPath("$.resetTimeSeconds").value(1700000000L))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturn400WhenIllegalArgumentThrown() throws Exception {

        when(gitHubUserService.getUser("invalid-user"))
                .thenThrow(new IllegalArgumentException("Invalid username format"));

        mockMvc.perform(get("/api/users/invalid-user")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Invalid username format"))
                .andExpect(jsonPath("$.status")
                        .value(400))
                .andExpect(jsonPath("$.timestamp")
                        .exists());
    }

    @Test
    void shouldReturn403WhenGitHubForbiddenThrown() throws Exception {
        when(gitHubUserService.getUser("forbidden-user"))
                .thenThrow(new com.gitracker.exception.GitHubForbiddenException("GitHub access to this resource is currently unavailable."));

        mockMvc.perform(get("/api/users/forbidden-user")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("GitHub access to this resource is currently unavailable."))
                .andExpect(jsonPath("$.status")
                        .value(403))
                .andExpect(jsonPath("$.timestamp")
                        .exists());
    }

    @Test
    void shouldReturn401WhenGitHubAuthenticationThrown() throws Exception {
        when(gitHubUserService.getUser("auth-user"))
                .thenThrow(new com.gitracker.exception.GitHubAuthenticationException("Invalid or expired GitHub access token."));

        mockMvc.perform(get("/api/users/auth-user")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Invalid or expired GitHub access token."))
                .andExpect(jsonPath("$.status")
                        .value(401))
                .andExpect(jsonPath("$.timestamp")
                        .exists());
    }

    @Test
    void shouldReturn400WhenGitHubValidationThrown() throws Exception {
        when(gitHubUserService.getUser("invalid-format"))
                .thenThrow(new com.gitracker.exception.GitHubValidationException("GitHub API unprocessable entity for user."));

        mockMvc.perform(get("/api/users/invalid-format")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("GitHub API unprocessable entity for user."))
                .andExpect(jsonPath("$.status")
                        .value(400))
                .andExpect(jsonPath("$.timestamp")
                        .exists());
    }

    @Test
    void shouldReturn503WhenGitHubServiceUnavailableThrown() throws Exception {
        when(gitHubUserService.getUser("unavailable-user"))
                .thenThrow(new com.gitracker.exception.GitHubServiceUnavailableException("GitHub is temporarily unavailable. Please try again shortly."));

        mockMvc.perform(get("/api/users/unavailable-user")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message")
                        .value("GitHub is temporarily unavailable. Please try again shortly."))
                .andExpect(jsonPath("$.status")
                        .value(503))
                .andExpect(jsonPath("$.timestamp")
                        .exists());
    }

    @Test
    void shouldReturn500WhenUnexpectedExceptionOccurs() throws Exception {

        when(gitHubUserService.getUser("crash-user"))
                .thenThrow(new RuntimeException("Database connectivity lost"));

        mockMvc.perform(get("/api/users/crash-user")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message")
                        .value("An unexpected internal error occurred. Please try again later."))
                .andExpect(jsonPath("$.status")
                        .value(500))
                .andExpect(jsonPath("$.timestamp")
                        .exists());
    }
}