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