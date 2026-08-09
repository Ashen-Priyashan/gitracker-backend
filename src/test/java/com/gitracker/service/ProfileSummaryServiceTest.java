package com.gitracker.service;

import com.gitracker.dto.GitHubUserResponse;
import com.gitracker.dto.ProfileSummaryResponse;
import com.gitracker.dto.RepositoryResponse;
import com.gitracker.entity.UserAnalytics;
import com.gitracker.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileSummaryServiceTest {

    @Mock
    private GitHubUserService gitHubUserService;

    @Mock
    private GitHubRepositoryService gitHubRepositoryService;

    @Mock
    private UserAnalyticsService userAnalyticsService;

    @InjectMocks
    private ProfileSummaryService profileSummaryService;

    @Test
    void shouldReturnProfileSummary() {

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
                .totalRepositories(2)
                .totalStars(30)
                .totalForks(10)
                .mostUsedLanguage("Java")
                .build();

        RepositoryResponse repository = new RepositoryResponse(
                "gitracker",
                "GitHub analytics",
                30,
                10,
                30,
                "Java",
                "main",
                0,
                "MIT License",
                List.of("analytics"),
                "https://github.com/octocat/gitracker",
                1024,
                false,
                "2024-01-01T00:00:00Z",
                "2024-01-02T00:00:00Z"
        );

        when(gitHubUserService.getUser("octocat"))
                .thenReturn(user);

        when(gitHubRepositoryService.getRepositories("octocat"))
                .thenReturn(List.of(repository));

        when(userAnalyticsService.generateAnalytics("octocat"))
                .thenReturn(analytics);

        // Act
        ProfileSummaryResponse response =
                profileSummaryService.getProfileSummary("octocat");

        // Assert
        assertEquals(user, response.user());
        assertEquals(analytics, response.analytics());
        assertEquals(1, response.repositories().size());

        verify(gitHubUserService).getUser("octocat");
        verify(gitHubRepositoryService).getRepositories("octocat");
        verify(userAnalyticsService).generateAnalytics("octocat");
    }

    @Test
    void shouldThrowExceptionWhenUserDoesNotExist() {

        // Arrange
        when(gitHubUserService.getUser("octocat"))
                .thenThrow(
                        new ResourceNotFoundException(
                                "GitHub user not found"
                        )
                );

        // Act + Assert
        assertThrows(
                ResourceNotFoundException.class,
                () -> profileSummaryService.getProfileSummary("octocat")
        );

        verify(gitHubRepositoryService, never())
                .getRepositories(anyString());

        verify(userAnalyticsService, never())
                .generateAnalytics(anyString());
    }
}