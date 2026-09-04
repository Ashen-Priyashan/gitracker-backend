package com.gitracker.service;

import com.gitracker.dto.GitHubUserResponse;
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

    @Mock
    private GitHubUserService gitHubUserService;

    @Mock
    private GitHubRepositoryService gitHubRepositoryService;

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
    void shouldHandleNullStarsAndForksWithoutNullPointerException() {
        GitHubUser user = GitHubUser.builder()
                .username("octocat")
                .build();

        Repository repoWithNulls = Repository.builder()
                .stars(null)
                .forks(null)
                .language("TypeScript")
                .owner(user)
                .build();

        Repository normalRepo = Repository.builder()
                .stars(5)
                .forks(2)
                .language("TypeScript")
                .owner(user)
                .build();

        when(gitHubUserRepository.findByUsername("octocat"))
                .thenReturn(Optional.of(user));

        when(repositoryRepository.findByOwner(user))
                .thenReturn(List.of(repoWithNulls, normalRepo));

        when(userAnalyticsRepository.findByGithubUser(user))
                .thenReturn(Optional.empty());

        when(userAnalyticsRepository.save(any(UserAnalytics.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserAnalytics analytics = userAnalyticsService.generateAnalytics("octocat");

        assertEquals(2, analytics.getTotalRepositories());
        assertEquals(5, analytics.getTotalStars());
        assertEquals(2, analytics.getTotalForks());
        assertEquals("TypeScript", analytics.getMostUsedLanguage());
    }

    @Test
    void shouldSyncUserAndRepositoriesWhenNotCachedLocally() {
        GitHubUser user = GitHubUser.builder()
                .username("octocat")
                .build();

        Repository repo = Repository.builder()
                .stars(15)
                .forks(3)
                .language("Kotlin")
                .owner(user)
                .build();

        when(gitHubUserRepository.findByUsername("octocat"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(user));

        when(gitHubUserService.getUser("octocat"))
                .thenReturn(new GitHubUserResponse("octocat", "Octocat", null, null, 10, 5, null, null));

        when(repositoryRepository.findByOwner(user))
                .thenReturn(List.of())
                .thenReturn(List.of(repo));

        when(userAnalyticsRepository.findByGithubUser(user))
                .thenReturn(Optional.empty());

        when(userAnalyticsRepository.save(any(UserAnalytics.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserAnalytics analytics = userAnalyticsService.generateAnalytics("octocat");

        assertEquals(1, analytics.getTotalRepositories());
        assertEquals(15, analytics.getTotalStars());
        assertEquals(3, analytics.getTotalForks());
        assertEquals("Kotlin", analytics.getMostUsedLanguage());

        verify(gitHubUserService).getUser("octocat");
        verify(gitHubRepositoryService).getRepositories("octocat");
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

        doThrow(new ResourceNotFoundException("GitHub user not found"))
                .when(gitHubUserService).getUser("octocat");

        // Act + Assert
        assertThrows(
                ResourceNotFoundException.class,
                () -> userAnalyticsService.generateAnalytics("octocat")
        );

        verify(repositoryRepository, never()).findByOwner(any());
    }
}