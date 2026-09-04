package com.gitracker.service;

import com.gitracker.client.GitHubApiClient;
import com.gitracker.dto.GitHubRepositoryDto;
import com.gitracker.dto.RepositoryAnalyticsResponse;
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
class RepositoryAnalyticsServiceTest {

    @Mock
    private GitHubApiClient gitHubApiClient;

    @InjectMocks
    private RepositoryAnalyticsService repositoryAnalyticsService;

    private GitHubRepositoryDto createRepo(String name, int stars, int forks, int watchers) {
        return new GitHubRepositoryDto(
                1L,
                name,
                "Description for " + name,
                stars,
                forks,
                watchers,
                "Java",
                "main",
                0,
                null,
                List.of(),
                "https://github.com/test/" + name,
                200,
                false,
                "2024-01-01T00:00:00Z",
                "2024-01-02T00:00:00Z"
        );
    }

    @Test
    void shouldCalculateRepositoryTotalsAndTopRepositoriesCorrectly() {
        GitHubRepositoryDto[] repos = new GitHubRepositoryDto[]{
                createRepo("repo-stars", 100, 10, 20),
                createRepo("repo-forks", 50, 80, 15),
                createRepo("repo-watchers", 30, 25, 95)
        };

        when(gitHubApiClient.getRepositories("octocat")).thenReturn(repos);

        RepositoryAnalyticsResponse response = repositoryAnalyticsService.getRepositoryAnalytics("octocat");

        assertNotNull(response);
        assertEquals(3, response.totalRepositories());
        assertEquals(180, response.totalStars());
        assertEquals(115, response.totalForks());
        assertEquals(130, response.totalWatchers());
        assertEquals("repo-stars", response.mostStarredRepository());
        assertEquals("repo-forks", response.mostForkedRepository());
        assertEquals("repo-watchers", response.mostWatchedRepository());

        verify(gitHubApiClient).getRepositories("octocat");
    }

    @Test
    void shouldHandleEmptyRepositoryList() {
        when(gitHubApiClient.getRepositories("octocat")).thenReturn(new GitHubRepositoryDto[0]);

        RepositoryAnalyticsResponse response = repositoryAnalyticsService.getRepositoryAnalytics("octocat");

        assertNotNull(response);
        assertEquals(0, response.totalRepositories());
        assertEquals(0, response.totalStars());
        assertEquals(0, response.totalForks());
        assertEquals(0, response.totalWatchers());
        assertEquals("N/A", response.mostStarredRepository());
        assertEquals("N/A", response.mostForkedRepository());
        assertEquals("N/A", response.mostWatchedRepository());

        verify(gitHubApiClient).getRepositories("octocat");
    }

    @Test
    void shouldHandleSingleRepository() {
        GitHubRepositoryDto[] repos = new GitHubRepositoryDto[]{
                createRepo("solo-repo", 42, 17, 33)
        };

        when(gitHubApiClient.getRepositories("octocat")).thenReturn(repos);

        RepositoryAnalyticsResponse response = repositoryAnalyticsService.getRepositoryAnalytics("octocat");

        assertNotNull(response);
        assertEquals(1, response.totalRepositories());
        assertEquals(42, response.totalStars());
        assertEquals(17, response.totalForks());
        assertEquals(33, response.totalWatchers());
        assertEquals("solo-repo", response.mostStarredRepository());
        assertEquals("solo-repo", response.mostForkedRepository());
        assertEquals("solo-repo", response.mostWatchedRepository());

        verify(gitHubApiClient).getRepositories("octocat");
    }

    @Test
    void shouldPropagateExceptionWhenApiClientFails() {
        when(gitHubApiClient.getRepositories("octocat"))
                .thenThrow(new ResourceNotFoundException("User not found"));

        assertThrows(ResourceNotFoundException.class, () ->
                repositoryAnalyticsService.getRepositoryAnalytics("octocat"));

        verify(gitHubApiClient).getRepositories("octocat");
    }
}
