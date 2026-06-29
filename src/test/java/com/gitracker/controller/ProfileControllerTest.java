package com.gitracker.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gitracker.dto.GitHubUserResponse;
import com.gitracker.dto.ProfileSummaryResponse;
import com.gitracker.dto.RepositoryResponse;
import com.gitracker.entity.UserAnalytics;
import com.gitracker.service.ProfileSummaryService;

@WebMvcTest(ProfileController.class)
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileSummaryService profileSummaryService;

    @Test
    void shouldReturnProfileSummary() throws Exception {

        // Arrange
        GitHubUserResponse user = new GitHubUserResponse(
                "octocat",
                "The Octocat",
                "GitHub mascot",
                "San Francisco",
                100,
                10,
                "https://avatars.githubusercontent.com/u/583231?v=4",
                "2024-01-01T00:00:00Z"
        );

        UserAnalytics analytics = UserAnalytics.builder()
                .totalRepositories(8)
                .totalStars(21598)
                .totalForks(164958)
                .mostUsedLanguage("CSS")
                .calculatedAt(LocalDateTime.now())
                .build();

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

        ProfileSummaryResponse response =
                new ProfileSummaryResponse(
                        user,
                        analytics,
                        List.of(repository)
                );

        when(profileSummaryService.getProfileSummary("octocat"))
                .thenReturn(response);

        // Act + Assert
        mockMvc.perform(get("/api/profile/octocat")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("octocat"))
                .andExpect(jsonPath("$.user.name").value("The Octocat"))
                .andExpect(jsonPath("$.analytics.totalRepositories").value(8))
                .andExpect(jsonPath("$.analytics.totalStars").value(21598))
                .andExpect(jsonPath("$.repositories[0].name").value("gitracker"))
                .andExpect(jsonPath("$.repositories[0].language").value("Java"));
    }
}