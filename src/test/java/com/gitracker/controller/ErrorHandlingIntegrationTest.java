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
}