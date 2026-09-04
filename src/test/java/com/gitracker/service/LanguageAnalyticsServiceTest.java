package com.gitracker.service;

import com.gitracker.client.GitHubApiClient;
import com.gitracker.dto.GitHubRepositoryDto;
import com.gitracker.dto.LanguageAnalyticsResponse;
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
class LanguageAnalyticsServiceTest {

    @Mock
    private GitHubApiClient gitHubApiClient;

    @InjectMocks
    private LanguageAnalyticsService languageAnalyticsService;

    private GitHubRepositoryDto createRepo(String name, String language) {
        return new GitHubRepositoryDto(
                1L,
                name,
                "Description",
                10,
                5,
                10,
                language,
                "main",
                0,
                null,
                List.of(),
                "https://github.com/test/" + name,
                100,
                false,
                "2024-01-01T00:00:00Z",
                "2024-01-02T00:00:00Z"
        );
    }

    @Test
    void shouldCalculateLanguagePercentagesAndMostUsedLanguageCorrectly() {
        GitHubRepositoryDto[] repos = new GitHubRepositoryDto[]{
                createRepo("repo1", "Java"),
                createRepo("repo2", "Java"),
                createRepo("repo3", "TypeScript"),
                createRepo("repo4", "Python")
        };

        when(gitHubApiClient.getRepositories("octocat")).thenReturn(repos);

        LanguageAnalyticsResponse response = languageAnalyticsService.getLanguageAnalytics("octocat");

        assertNotNull(response);
        assertEquals(4, response.totalRepositories());
        assertEquals("Java", response.mostUsedLanguage());
        assertEquals(50.0, response.languageBreakdown().get("Java"));
        assertEquals(25.0, response.languageBreakdown().get("TypeScript"));
        assertEquals(25.0, response.languageBreakdown().get("Python"));

        verify(gitHubApiClient).getRepositories("octocat");
    }

    @Test
    void shouldHandleEmptyRepositoryList() {
        when(gitHubApiClient.getRepositories("octocat")).thenReturn(new GitHubRepositoryDto[0]);

        LanguageAnalyticsResponse response = languageAnalyticsService.getLanguageAnalytics("octocat");

        assertNotNull(response);
        assertEquals(0, response.totalRepositories());
        assertEquals("Unknown", response.mostUsedLanguage());
        assertTrue(response.languageBreakdown().isEmpty());

        verify(gitHubApiClient).getRepositories("octocat");
    }

    @Test
    void shouldHandleNullAndBlankLanguagesAsUnknown() {
        GitHubRepositoryDto[] repos = new GitHubRepositoryDto[]{
                createRepo("repo1", null),
                createRepo("repo2", "   "),
                createRepo("repo3", "Go")
        };

        when(gitHubApiClient.getRepositories("octocat")).thenReturn(repos);

        LanguageAnalyticsResponse response = languageAnalyticsService.getLanguageAnalytics("octocat");

        assertNotNull(response);
        assertEquals(3, response.totalRepositories());
        assertEquals("Unknown", response.mostUsedLanguage());
        assertEquals(66.67, response.languageBreakdown().get("Unknown"));
        assertEquals(33.33, response.languageBreakdown().get("Go"));

        verify(gitHubApiClient).getRepositories("octocat");
    }

    @Test
    void shouldHandleSingleRepository() {
        GitHubRepositoryDto[] repos = new GitHubRepositoryDto[]{
                createRepo("repo1", "Rust")
        };

        when(gitHubApiClient.getRepositories("octocat")).thenReturn(repos);

        LanguageAnalyticsResponse response = languageAnalyticsService.getLanguageAnalytics("octocat");

        assertNotNull(response);
        assertEquals(1, response.totalRepositories());
        assertEquals("Rust", response.mostUsedLanguage());
        assertEquals(100.0, response.languageBreakdown().get("Rust"));

        verify(gitHubApiClient).getRepositories("octocat");
    }

    @Test
    void shouldPropagateExceptionWhenApiClientThrows() {
        when(gitHubApiClient.getRepositories("octocat"))
                .thenThrow(new ResourceNotFoundException("User not found"));

        assertThrows(ResourceNotFoundException.class, () ->
                languageAnalyticsService.getLanguageAnalytics("octocat"));

        verify(gitHubApiClient).getRepositories("octocat");
    }
}
