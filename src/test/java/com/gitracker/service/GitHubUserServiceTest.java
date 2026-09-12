package com.gitracker.service;

import com.gitracker.client.GitHubApiClient;
import com.gitracker.dto.GitHubUserDto;
import com.gitracker.dto.GitHubUserResponse;
import com.gitracker.entity.GitHubUser;
import com.gitracker.repository.GitHubUserRepository;
import com.gitracker.exception.GitHubAuthenticationException;
import com.gitracker.exception.GitHubForbiddenException;
import com.gitracker.exception.GitHubServiceUnavailableException;
import com.gitracker.exception.GitHubValidationException;
import com.gitracker.exception.RateLimitExceededException;
import com.gitracker.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubUserServiceTest {

    @Mock
    private GitHubApiClient gitHubApiClient;

    @Mock
    private GitHubUserRepository gitHubUserRepository;

    private GitHubUserService gitHubUserService;

    @BeforeEach
    void setUp() {
        gitHubUserService = new GitHubUserService(gitHubApiClient, gitHubUserRepository, 15L);
    }

    private GitHubUser createStaleUser() {
        return GitHubUser.builder()
                .githubId(583231L)
                .username("octocat")
                .name("Old Name")
                .bio("Old Bio")
                .avatarUrl("https://avatars.githubusercontent.com/u/583231?v=4")
                .followers(50)
                .following(5)
                .publicRepos(4)
                .updatedAt(LocalDateTime.now().minusMinutes(30))
                .build();
    }

    @Test
    void shouldReturnUserFromDatabase() {

        // Arrange
        GitHubUser user = GitHubUser.builder()
                .githubId(583231L)
                .username("octocat")
                .name("The Octocat")
                .bio("GitHub mascot")
                .avatarUrl("https://avatars.githubusercontent.com/u/583231?v=4")
                .followers(100)
                .following(10)
                .publicRepos(8)
                .createdAt(LocalDateTime.now())
                .build();

        when(gitHubUserRepository.findByUsernameIgnoreCase("octocat"))
                .thenReturn(Optional.of(user));

        // Act
        GitHubUserResponse response =
                gitHubUserService.getUser("octocat");

        // Assert
        assertEquals("octocat", response.username());
        assertEquals("The Octocat", response.name());
        assertNull(response.createdAt(), "Legacy users without stored githubCreatedAt should have null createdAt in response");

        verify(gitHubApiClient, never()).getUser(anyString());
        verify(gitHubUserRepository, never()).save(any());
    }

    @Test
    void shouldReturnCachedUserWithStoredGithubCreatedAt() {
        // Arrange
        GitHubUser user = GitHubUser.builder()
                .githubId(583231L)
                .username("octocat")
                .name("The Octocat")
                .followers(100)
                .following(10)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .githubCreatedAt(LocalDateTime.of(2020, 5, 1, 12, 0))
                .build();

        when(gitHubUserRepository.findByUsernameIgnoreCase("octocat"))
                .thenReturn(Optional.of(user));

        // Act
        GitHubUserResponse response = gitHubUserService.getUser("octocat");

        // Assert
        assertEquals("octocat", response.username());
        assertEquals("2020-05-01T12:00", response.createdAt());
    }

    @Test
    void shouldFetchUserFromGitHubWhenNotInDatabase() {

        // Arrange
        GitHubUserDto userDto = new GitHubUserDto(
                583231L,
                "octocat",
                "The Octocat",
                "GitHub mascot",
                "San Francisco",
                100,
                10,
                8,
                "https://avatars.githubusercontent.com/u/583231?v=4",
                "2024-01-01T00:00:00Z"
        );

        when(gitHubUserRepository.findByUsernameIgnoreCase("octocat"))
                .thenReturn(Optional.empty());

        when(gitHubApiClient.getUser("octocat"))
                .thenReturn(userDto);

        // Act
        GitHubUserResponse response =
                gitHubUserService.getUser("octocat");

        // Assert
        assertEquals("octocat", response.username());
        assertEquals("The Octocat", response.name());

        verify(gitHubApiClient).getUser("octocat");
        verify(gitHubUserRepository).save(any(GitHubUser.class));
    }

    @Test
    void shouldRefreshStaleUserFromGitHub() {

        // Arrange: user was last updated 30 minutes ago (stale against 15m default TTL)
        GitHubUser staleUser = createStaleUser();

        GitHubUserDto freshDto = new GitHubUserDto(
                583231L,
                "octocat",
                "Fresh Octocat",
                "Updated Bio",
                "San Francisco",
                200,
                20,
                15,
                "https://avatars.githubusercontent.com/u/583231?v=4",
                "2024-01-01T00:00:00Z"
        );

        when(gitHubUserRepository.findByUsernameIgnoreCase("octocat"))
                .thenReturn(Optional.of(staleUser));

        when(gitHubApiClient.getUser("octocat"))
                .thenReturn(freshDto);

        // Act
        GitHubUserResponse response = gitHubUserService.getUser("octocat");

        // Assert
        assertEquals("Fresh Octocat", response.name());
        assertEquals("Updated Bio", response.bio());
        assertEquals(200, response.followers());
        assertEquals(20, response.following());

        verify(gitHubApiClient).getUser("octocat");
        verify(gitHubUserRepository).save(staleUser);
        assertEquals("Fresh Octocat", staleUser.getName());
    }

    @Test
    void shouldPropagateRateLimitExceededExceptionWhenRefreshingStaleUser() {
        GitHubUser staleUser = createStaleUser();
        when(gitHubUserRepository.findByUsernameIgnoreCase("octocat"))
                .thenReturn(Optional.of(staleUser));
        when(gitHubApiClient.getUser("octocat"))
                .thenThrow(new RateLimitExceededException("GitHub API rate limit exceeded", 60L, 1740000000L));

        assertThrows(RateLimitExceededException.class, () -> gitHubUserService.getUser("octocat"));
        verify(gitHubApiClient).getUser("octocat");
        verify(gitHubUserRepository, never()).save(any());
    }

    @Test
    void shouldPropagateAuthenticationExceptionWhenRefreshingStaleUser() {
        GitHubUser staleUser = createStaleUser();
        when(gitHubUserRepository.findByUsernameIgnoreCase("octocat"))
                .thenReturn(Optional.of(staleUser));
        when(gitHubApiClient.getUser("octocat"))
                .thenThrow(new GitHubAuthenticationException("Bad credentials"));

        assertThrows(GitHubAuthenticationException.class, () -> gitHubUserService.getUser("octocat"));
        verify(gitHubApiClient).getUser("octocat");
        verify(gitHubUserRepository, never()).save(any());
    }

    @Test
    void shouldPropagateForbiddenExceptionWhenRefreshingStaleUser() {
        GitHubUser staleUser = createStaleUser();
        when(gitHubUserRepository.findByUsernameIgnoreCase("octocat"))
                .thenReturn(Optional.of(staleUser));
        when(gitHubApiClient.getUser("octocat"))
                .thenThrow(new GitHubForbiddenException("Resource forbidden"));

        assertThrows(GitHubForbiddenException.class, () -> gitHubUserService.getUser("octocat"));
        verify(gitHubApiClient).getUser("octocat");
        verify(gitHubUserRepository, never()).save(any());
    }

    @Test
    void shouldPropagateResourceNotFoundExceptionWhenRefreshingStaleUser() {
        GitHubUser staleUser = createStaleUser();
        when(gitHubUserRepository.findByUsernameIgnoreCase("octocat"))
                .thenReturn(Optional.of(staleUser));
        when(gitHubApiClient.getUser("octocat"))
                .thenThrow(new ResourceNotFoundException("GitHub user octocat not found"));

        assertThrows(ResourceNotFoundException.class, () -> gitHubUserService.getUser("octocat"));
        verify(gitHubApiClient).getUser("octocat");
        verify(gitHubUserRepository, never()).save(any());
    }

    @Test
    void shouldPropagateValidationExceptionWhenRefreshingStaleUser() {
        GitHubUser staleUser = createStaleUser();
        when(gitHubUserRepository.findByUsernameIgnoreCase("octocat"))
                .thenReturn(Optional.of(staleUser));
        when(gitHubApiClient.getUser("octocat"))
                .thenThrow(new GitHubValidationException("Validation failed"));

        assertThrows(GitHubValidationException.class, () -> gitHubUserService.getUser("octocat"));
        verify(gitHubApiClient).getUser("octocat");
        verify(gitHubUserRepository, never()).save(any());
    }

    @Test
    void shouldFallbackToCachedUserOn503ServiceUnavailableWhenRefreshingStaleUser() {
        GitHubUser staleUser = createStaleUser();
        when(gitHubUserRepository.findByUsernameIgnoreCase("octocat"))
                .thenReturn(Optional.of(staleUser));
        when(gitHubApiClient.getUser("octocat"))
                .thenThrow(new GitHubServiceUnavailableException("GitHub service is temporarily unavailable"));

        GitHubUserResponse response = gitHubUserService.getUser("octocat");

        assertEquals("octocat", response.username());
        assertEquals("Old Name", response.name());
        assertEquals("Old Bio", response.bio());
        verify(gitHubApiClient).getUser("octocat");
    }

    @Test
    void shouldFallbackToCachedUserOnNetworkFailureWhenRefreshingStaleUser() {
        GitHubUser staleUser = createStaleUser();
        when(gitHubUserRepository.findByUsernameIgnoreCase("octocat"))
                .thenReturn(Optional.of(staleUser));
        when(gitHubApiClient.getUser("octocat"))
                .thenThrow(new ResourceAccessException("Connection timed out to api.github.com"));

        GitHubUserResponse response = gitHubUserService.getUser("octocat");

        assertEquals("octocat", response.username());
        assertEquals("Old Name", response.name());
        assertEquals("Old Bio", response.bio());
        verify(gitHubApiClient).getUser("octocat");
    }
}