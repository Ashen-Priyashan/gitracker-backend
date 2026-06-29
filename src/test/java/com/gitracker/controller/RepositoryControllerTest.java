package com.gitracker.controller;

import com.gitracker.dto.RepositoryResponse;
import com.gitracker.service.GitHubRepositoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RepositoryController.class)
class RepositoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitHubRepositoryService repositoryService;

    @Test
    void shouldReturnRepositories() throws Exception {

        RepositoryResponse repository = new RepositoryResponse(
                "gitracker",
                "GitHub analytics",
                100,
                20,
                100,
                "Java",
                "2024-01-01T00:00:00Z",
                "2024-01-02T00:00:00Z"
        );

        when(repositoryService.getRepositories("octocat"))
                .thenReturn(List.of(repository));

        mockMvc.perform(get("/api/users/octocat/repositories")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("gitracker"))
                .andExpect(jsonPath("$[0].language").value("Java"))
                .andExpect(jsonPath("$[0].stars").value(100))
                .andExpect(jsonPath("$[0].forks").value(20));
    }
}