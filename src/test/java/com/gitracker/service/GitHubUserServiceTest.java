package com.gitracker.service;

import com.gitracker.client.GitHubApiClient;
import com.gitracker.dto.GitHubUserDto;
import com.gitracker.dto.GitHubUserResponse;
import com.gitracker.entity.GitHubUser;
import com.gitracker.repository.GitHubUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

        verify(gitHubApiClient, never()).getUser(anyString());
        verify(gitHubUserRepository, never()).save(any());
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
        GitHubUser staleUser = GitHubUser.builder()
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
    void shouldFallbackToCachedUserWhenRefreshFails() {

        // Arrange: stale user, but GitHub API throws exception (e.g. rate limited)
        GitHubUser staleUser = GitHubUser.builder()
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

        when(gitHubUserRepository.findByUsernameIgnoreCase("octocat"))
                .thenReturn(Optional.of(staleUser));

        when(gitHubApiClient.getUser("octocat"))
                .thenThrow(new RuntimeException("GitHub API rate limit exceeded"));

        // Act: should not throw, gracefully fall back to cached user
        GitHubUserResponse response = gitHubUserService.getUser("octocat");

        // Assert
        assertEquals("octocat", response.username());
        assertEquals("Old Name", response.name());
        assertEquals("Old Bio", response.bio());

        verify(gitHubApiClient).getUser("octocat");
    }
}