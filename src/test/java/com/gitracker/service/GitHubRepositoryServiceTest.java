package com.gitracker.service;

import com.gitracker.client.GitHubApiClient;
import com.gitracker.dto.*;
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
        verify(gitHubUserRepository).save(owner);
    }

    @Test
    void shouldUpdateAndSaveExistingRepository() {

        // Arrange
        GitHubRepositoryDto repositoryDto = createSampleDto();

        GitHubUser owner = GitHubUser.builder()
                .username("octocat")
                .build();

        Repository existingRepository = Repository.builder()
                .repoId(1L)
                .name("old-name")
                .stars(0)
                .owner(owner)
                .build();

        when(gitHubApiClient.getRepositories("octocat"))
                .thenReturn(new GitHubRepositoryDto[]{repositoryDto});

        when(gitHubUserRepository.findByUsername("octocat"))
                .thenReturn(Optional.of(owner));

        when(repositoryRepository.findByOwner(owner))
                .thenReturn(List.of(existingRepository));

        // Act
        List<RepositoryResponse> response = gitHubRepositoryService.getRepositories("octocat");

        // Assert
        assertEquals(1, response.size());
        assertEquals("gitracker", existingRepository.getName());
        assertEquals(100, existingRepository.getStars());
        verify(repositoryRepository).save(existingRepository);
        verify(gitHubUserRepository).save(owner);
    }

    @Test
    void shouldGetRepositoryDetailsAndSaveIfNew() {
        GitHubRepositoryDto repositoryDto = createSampleDto();
        GitHubUser owner = GitHubUser.builder().username("octocat").build();

        when(gitHubApiClient.getRepository("octocat", "gitracker")).thenReturn(repositoryDto);
        when(gitHubUserRepository.findByUsername("octocat")).thenReturn(Optional.of(owner));
        when(repositoryRepository.findByOwner(owner)).thenReturn(List.of());

        RepositoryResponse response = gitHubRepositoryService.getRepository("octocat", "gitracker");

        assertNotNull(response);
        assertEquals("gitracker", response.name());
        verify(repositoryRepository).save(any(Repository.class));
    }

    @Test
    void shouldGetRepositoryDetailsWithoutSavingIfAlreadyExists() {
        GitHubRepositoryDto repositoryDto = createSampleDto();
        GitHubUser owner = GitHubUser.builder().username("octocat").build();
        Repository existing = Repository.builder().repoId(1L).owner(owner).build();

        when(gitHubApiClient.getRepository("octocat", "gitracker")).thenReturn(repositoryDto);
        when(gitHubUserRepository.findByUsername("octocat")).thenReturn(Optional.of(owner));
        when(repositoryRepository.findByOwner(owner)).thenReturn(List.of(existing));

        RepositoryResponse response = gitHubRepositoryService.getRepository("octocat", "gitracker");

        assertNotNull(response);
        assertEquals("gitracker", response.name());
        verify(repositoryRepository, never()).save(any(Repository.class));
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

    @Test
    void shouldGetBranchesAndMapCorrectly() {
        GitHubBranchDto b1 = new GitHubBranchDto("main", false, new GitHubBranchCommitDto("sha1", "url1"));
        GitHubBranchDto b2 = new GitHubBranchDto("release/v1", true, new GitHubBranchCommitDto("sha2", "url2"));

        when(gitHubApiClient.getBranches("octocat", "gitracker"))
                .thenReturn(new GitHubBranchDto[]{b1, b2});

        List<BranchResponse> branches = gitHubRepositoryService.getBranches("octocat", "gitracker");

        assertNotNull(branches);
        assertEquals(2, branches.size());
        assertEquals("main", branches.get(0).name());
        assertFalse(branches.get(0).isProtected());
        assertEquals("sha1", branches.get(0).commitSha());
        assertEquals("release/v1", branches.get(1).name());
        assertTrue(branches.get(1).isProtected());
        assertEquals("sha2", branches.get(1).commitSha());
    }

    @Test
    void shouldGetCommitsAndMapAuthorFallback() {
        GitHubGitCommitDto gitCommit = new GitHubGitCommitDto(
                new GitHubCommitUserDto("Git Author", "author@example.com", "2026-09-01T10:00:00Z"),
                new GitHubCommitUserDto("Git Committer", "committer@example.com", "2026-09-01T10:05:00Z"),
                "Commit message title\n\nBody explanation"
        );

        // Commit with null GitHub user author (fallback to Git author)
        GitHubCommitListDto commitDto = new GitHubCommitListDto(
                "abcdef1234567890",
                "node_1",
                gitCommit,
                "https://github.com/octocat/gitracker/commit/abcdef1234567890",
                null,
                null,
                List.of(new GitHubParentDto("parentsha1", "url", "https://github.com/octocat/gitracker/commit/parentsha1"))
        );

        when(gitHubApiClient.getCommits("octocat", "gitracker", "main", 1, 20))
                .thenReturn(new GitHubCommitListDto[]{commitDto});

        List<CommitSummaryResponse> commits = gitHubRepositoryService.getCommits("octocat", "gitracker", "main", 1, 20);

        assertNotNull(commits);
        assertEquals(1, commits.size());
        CommitSummaryResponse summary = commits.getFirst();
        assertEquals("abcdef1234567890", summary.sha());
        assertEquals("abcdef1", summary.shortSha());
        assertEquals("Git Author", summary.authorName());
        assertNull(summary.authorLogin());
        assertNull(summary.authorAvatarUrl());
        assertEquals("Git Committer", summary.committerName());
        assertEquals("2026-09-01T10:00:00Z", summary.authoredAt());
        assertEquals("2026-09-01T10:05:00Z", summary.committedAt());
    }

    @Test
    void shouldGetCommitDetailAndMapFilesAndStats() {
        GitHubGitCommitDto gitCommit = new GitHubGitCommitDto(
                new GitHubCommitUserDto("Alice", "alice@example.com", "2026-09-01T10:00:00Z"),
                new GitHubCommitUserDto("Bob", "bob@example.com", "2026-09-01T10:05:00Z"),
                "Refactor authentication"
        );

        GitHubCommitAuthorDto authorUser = new GitHubCommitAuthorDto(1L, "alice_dev", "https://avatar.url/alice", "https://github.com/alice_dev");
        GitHubCommitAuthorDto committerUser = new GitHubCommitAuthorDto(2L, "bob_dev", "https://avatar.url/bob", "https://github.com/bob_dev");

        GitHubCommitFileDto file = new GitHubCommitFileDto(
                "sha_file",
                "src/Auth.java",
                "modified",
                15,
                5,
                20,
                "https://blob.url",
                "https://raw.url",
                "https://contents.url",
                "@@ -1,5 +1,15 @@",
                null
        );

        GitHubCommitDetailDto detailDto = new GitHubCommitDetailDto(
                "abcdef1234567890",
                "node_1",
                gitCommit,
                "https://github.com/octocat/gitracker/commit/abcdef1234567890",
                authorUser,
                committerUser,
                new GitHubCommitStatsDto(20, 15, 5),
                List.of(file),
                List.of(new GitHubParentDto("parent_sha", "parent_url", "https://github.com/octocat/gitracker/commit/parent_sha"))
        );

        when(gitHubApiClient.getCommit("octocat", "gitracker", "abcdef1234567890"))
                .thenReturn(detailDto);

        CommitDetailResponse response = gitHubRepositoryService.getCommit("octocat", "gitracker", "abcdef1234567890");

        assertNotNull(response);
        assertEquals("abcdef1234567890", response.sha());
        assertEquals("abcdef1", response.shortSha());
        assertEquals("Alice", response.authorName());
        assertEquals("alice_dev", response.authorLogin());
        assertEquals("https://avatar.url/alice", response.authorAvatarUrl());
        assertEquals("Bob", response.committerName());
        assertEquals("bob_dev", response.committerLogin());
        assertEquals(15, response.additions());
        assertEquals(5, response.deletions());
        assertEquals(20, response.totalChanges());
        assertEquals(1, response.changedFilesCount());
        assertEquals(1, response.parents().size());
        assertEquals("parent_sha", response.parents().get(0).sha());
        assertEquals(1, response.files().size());
        assertEquals("src/Auth.java", response.files().get(0).filename());
        assertEquals("modified", response.files().get(0).status());
        assertEquals("@@ -1,5 +1,15 @@", response.files().get(0).patch());
    }
}