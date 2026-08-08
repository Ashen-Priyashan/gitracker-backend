package com.gitracker.service;

import com.gitracker.client.GitHubApiClient;
import com.gitracker.dto.GitHubRepositoryDto;
import com.gitracker.dto.RepositoryResponse;
import com.gitracker.entity.GitHubUser;
import com.gitracker.entity.Repository;
import com.gitracker.repository.GitHubUserRepository;
import com.gitracker.repository.RepositoryRepository;
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
class GitHubRepositoryServiceTest {

    @Mock
    private GitHubApiClient gitHubApiClient;

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private GitHubUserRepository gitHubUserRepository;

    @InjectMocks
    private GitHubRepositoryService gitHubRepositoryService;

    private GitHubRepositoryDto createSampleDto() {
        return new GitHubRepositoryDto(
                1L,
                "gitracker",
                "GitHub analytics",
                100,
                20,
                100,
                "Java",
                "main",
                2,
                null,
                List.of("analytics", "github"),
                "https://github.com/octocat/gitracker",
                1024,
                false,
                "2024-01-01T00:00:00Z",
                "2024-01-02T00:00:00Z"
        );
    }

    @Test
    void shouldSaveNewRepository() {

        // Arrange
        GitHubRepositoryDto repositoryDto = createSampleDto();

        GitHubUser owner = GitHubUser.builder()
                .username("octocat")
                .build();

        when(gitHubApiClient.getRepositories("octocat"))
                .thenReturn(new GitHubRepositoryDto[]{repositoryDto});

        when(gitHubUserRepository.findByUsername("octocat"))
                .thenReturn(Optional.of(owner));

        when(repositoryRepository.findByOwner(owner))
                .thenReturn(List.of());

        // Act
        List<RepositoryResponse> response =
                gitHubRepositoryService.getRepositories("octocat");

        // Assert
        assertEquals(1, response.size());
        assertEquals("gitracker", response.getFirst().name());
        assertEquals("main", response.getFirst().defaultBranch());
        assertEquals(2, response.getFirst().openIssues());

        verify(repositoryRepository).save(any(Repository.class));
    }

    @Test
    void shouldNotSaveExistingRepository() {

        // Arrange
        GitHubRepositoryDto repositoryDto = createSampleDto();

        GitHubUser owner = GitHubUser.builder()
                .username("octocat")
                .build();

        Repository existingRepository = Repository.builder()
                .repoId(1L)
                .owner(owner)
                .build();

        when(gitHubApiClient.getRepositories("octocat"))
                .thenReturn(new GitHubRepositoryDto[]{repositoryDto});

        when(gitHubUserRepository.findByUsername("octocat"))
                .thenReturn(Optional.of(owner));

        when(repositoryRepository.findByOwner(owner))
                .thenReturn(List.of(existingRepository));

        // Act
        gitHubRepositoryService.getRepositories("octocat");

        // Assert
        verify(repositoryRepository, never()).save(any());
    }

    @Test
    void shouldReturnRepositoriesWhenOwnerDoesNotExist() {

        // Arrange
        GitHubRepositoryDto repositoryDto = createSampleDto();

        when(gitHubApiClient.getRepositories("octocat"))
                .thenReturn(new GitHubRepositoryDto[]{repositoryDto});

        when(gitHubUserRepository.findByUsername("octocat"))
                .thenReturn(Optional.empty());

        // Act
        List<RepositoryResponse> response =
                gitHubRepositoryService.getRepositories("octocat");

        // Assert
        assertEquals(1, response.size());

        verify(repositoryRepository, never()).save(any());
    }
}