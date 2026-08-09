package com.gitracker.service;

import com.gitracker.entity.GitHubUser;
import com.gitracker.entity.Repository;
import com.gitracker.entity.UserAnalytics;
import com.gitracker.exception.ResourceNotFoundException;
import com.gitracker.repository.GitHubUserRepository;
import com.gitracker.repository.RepositoryRepository;
import com.gitracker.repository.UserAnalyticsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAnalyticsServiceTest {

    @Mock
    private GitHubUserRepository gitHubUserRepository;

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private UserAnalyticsRepository userAnalyticsRepository;

    @InjectMocks
    private UserAnalyticsService userAnalyticsService;

    @Test
    void shouldGenerateAnalytics() {

        // Arrange
        GitHubUser user = GitHubUser.builder()
                .username("octocat")
                .build();

        Repository repo1 = Repository.builder()
                .stars(10)
                .forks(5)
                .language("Java")
                .owner(user)
                .build();

        Repository repo2 = Repository.builder()
                .stars(20)
                .forks(8)
                .language("Java")
                .owner(user)
                .build();

        Repository repo3 = Repository.builder()
                .stars(30)
                .forks(2)
                .language("Python")
                .owner(user)
                .build();

        when(gitHubUserRepository.findByUsername("octocat"))
                .thenReturn(Optional.of(user));

        when(repositoryRepository.findByOwner(user))
                .thenReturn(List.of(repo1, repo2, repo3));

        when(userAnalyticsRepository.findByGithubUser(user))
                .thenReturn(Optional.empty());

        when(userAnalyticsRepository.save(any(UserAnalytics.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserAnalytics analytics =
                userAnalyticsService.generateAnalytics("octocat");

        // Assert
        assertEquals(3, analytics.getTotalRepositories());
        assertEquals(60, analytics.getTotalStars());
        assertEquals(15, analytics.getTotalForks());
        assertEquals("Java", analytics.getMostUsedLanguage());

        verify(userAnalyticsRepository).save(any(UserAnalytics.class));
    }

    @Test
    void shouldUpdateExistingAnalytics() {

        // Arrange
        GitHubUser user = GitHubUser.builder()
                .username("octocat")
                .build();

        UserAnalytics existingAnalytics = UserAnalytics.builder()
                .githubUser(user)
                .build();

        when(gitHubUserRepository.findByUsername("octocat"))
                .thenReturn(Optional.of(user));

        when(repositoryRepository.findByOwner(user))
                .thenReturn(List.of());

        when(userAnalyticsRepository.findByGithubUser(user))
                .thenReturn(Optional.of(existingAnalytics));

        when(userAnalyticsRepository.save(any(UserAnalytics.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserAnalytics analytics =
                userAnalyticsService.generateAnalytics("octocat");

        // Assert
        assertSame(existingAnalytics, analytics);

        verify(userAnalyticsRepository).save(existingAnalytics);
    }

    @Test
    void shouldThrowExceptionWhenUserDoesNotExist() {

        // Arrange
        when(gitHubUserRepository.findByUsername("octocat"))
                .thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(
                ResourceNotFoundException.class,
                () -> userAnalyticsService.generateAnalytics("octocat")
        );

        verify(repositoryRepository, never()).findByOwner(any());
    }
}