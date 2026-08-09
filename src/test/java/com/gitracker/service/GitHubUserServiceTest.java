package com.gitracker.service;

import com.gitracker.client.GitHubApiClient;
import com.gitracker.dto.GitHubUserDto;
import com.gitracker.dto.GitHubUserResponse;
import com.gitracker.entity.GitHubUser;
import com.gitracker.repository.GitHubUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

    @InjectMocks
    private GitHubUserService gitHubUserService;

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

        when(gitHubUserRepository.findByUsername("octocat"))
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

        when(gitHubUserRepository.findByUsername("octocat"))
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
}