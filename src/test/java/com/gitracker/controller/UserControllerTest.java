package com.gitracker.controller;

import com.gitracker.dto.GitHubUserResponse;
import com.gitracker.service.GitHubUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitHubUserService gitHubUserService;

    @Test
    void shouldReturnUser() throws Exception {

        // Arrange
        GitHubUserResponse response = new GitHubUserResponse(
                "octocat",
                "The Octocat",
                "GitHub mascot",
                "San Francisco",
                100,
                10,
                "https://avatars.githubusercontent.com/u/583231?v=4",
                "2024-01-01T00:00:00Z"
        );

        when(gitHubUserService.getUser("octocat"))
                .thenReturn(response);

        // Act + Assert
        mockMvc.perform(get("/api/users/octocat")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("octocat"))
                .andExpect(jsonPath("$.name").value("The Octocat"))
                .andExpect(jsonPath("$.followers").value(100))
                .andExpect(jsonPath("$.following").value(10))
                .andExpect(jsonPath("$.avatarUrl")
                        .value("https://avatars.githubusercontent.com/u/583231?v=4"));
    }
}