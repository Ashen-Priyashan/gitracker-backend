package com.gitracker.controller;

import com.gitracker.dto.*;
import com.gitracker.service.GitHubRepositoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RepositoryController.class)
class RepositoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitHubRepositoryService repositoryService;

    @Test
    void shouldReturnRepositories() throws Exception {

        RepositoryResponse repository = new RepositoryResponse(
                "gitracker",
                "GitHub analytics",
                100,
                20,
                100,
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

        when(repositoryService.getRepositories("octocat"))
                .thenReturn(List.of(repository));

        mockMvc.perform(get("/api/users/octocat/repositories")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("gitracker"))
                .andExpect(jsonPath("$[0].language").value("Java"))
                .andExpect(jsonPath("$[0].stars").value(100))
                .andExpect(jsonPath("$[0].forks").value(20))
                .andExpect(jsonPath("$[0].defaultBranch").value("main"));
    }

    @Test
    void shouldReturnRepositoryDetails() throws Exception {
        RepositoryResponse repository = new RepositoryResponse(
                "gitracker",
                "GitHub analytics",
                100,
                20,
                100,
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

        when(repositoryService.getRepository("octocat", "gitracker"))
                .thenReturn(repository);

        mockMvc.perform(get("/api/users/octocat/repositories/gitracker")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("gitracker"))
                .andExpect(jsonPath("$.language").value("Java"))
                .andExpect(jsonPath("$.stars").value(100))
                .andExpect(jsonPath("$.forks").value(20))
                .andExpect(jsonPath("$.defaultBranch").value("main"));
    }

    @Test
    void shouldReturnBranches() throws Exception {
        BranchResponse b1 = new BranchResponse("main", false, "sha1");
        BranchResponse b2 = new BranchResponse("feature/auth", false, "sha2");

        when(repositoryService.getBranches("octocat", "gitracker"))
                .thenReturn(List.of(b1, b2));

        mockMvc.perform(get("/api/users/octocat/repositories/gitracker/branches")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("main"))
                .andExpect(jsonPath("$[0].isProtected").value(false))
                .andExpect(jsonPath("$[0].commitSha").value("sha1"))
                .andExpect(jsonPath("$[1].name").value("feature/auth"));
    }

    @Test
    void shouldReturnBranchCommits() throws Exception {
        CommitSummaryResponse commit = new CommitSummaryResponse(
                "abcdef1234567890",
                "abcdef1",
                "Fix authentication bug",
                "Alice",
                "alice_dev",
                "https://avatar.url/alice",
                "https://github.com/alice_dev",
                "Bob",
                "bob_dev",
                "https://avatar.url/bob",
                "https://github.com/bob_dev",
                "2026-09-01T10:00:00Z",
                "2026-09-01T10:05:00Z",
                "https://github.com/octocat/gitracker/commit/abcdef1234567890"
        );

        when(repositoryService.getCommits("octocat", "gitracker", "main", 1, 20))
                .thenReturn(List.of(commit));

        mockMvc.perform(get("/api/users/octocat/repositories/gitracker/commits")
                        .param("branch", "main")
                        .param("page", "1")
                        .param("perPage", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sha").value("abcdef1234567890"))
                .andExpect(jsonPath("$[0].shortSha").value("abcdef1"))
                .andExpect(jsonPath("$[0].message").value("Fix authentication bug"))
                .andExpect(jsonPath("$[0].authorName").value("Alice"))
                .andExpect(jsonPath("$[0].authorLogin").value("alice_dev"));
    }

    @Test
    void shouldReturnCommitDetail() throws Exception {
        CommitParentResponse parent = new CommitParentResponse("parent_sha", "https://github.com/octocat/gitracker/commit/parent_sha");
        CommitFileResponse file = new CommitFileResponse(
                "src/Auth.java",
                "modified",
                12,
                4,
                16,
                "https://blob.url",
                "https://raw.url",
                "@@ -1,4 +1,12 @@",
                null
        );

        CommitDetailResponse detail = new CommitDetailResponse(
                "abcdef1234567890",
                "abcdef1",
                "Fix authentication bug\n\nDetailed message",
                "Alice",
                "alice_dev",
                "https://avatar.url/alice",
                "https://github.com/alice_dev",
                "Bob",
                "bob_dev",
                "https://avatar.url/bob",
                "https://github.com/bob_dev",
                "2026-09-01T10:00:00Z",
                "2026-09-01T10:05:00Z",
                "https://github.com/octocat/gitracker/commit/abcdef1234567890",
                12,
                4,
                16,
                1,
                List.of(parent),
                List.of(file)
        );

        when(repositoryService.getCommit("octocat", "gitracker", "abcdef1234567890"))
                .thenReturn(detail);

        mockMvc.perform(get("/api/users/octocat/repositories/gitracker/commits/abcdef1234567890")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sha").value("abcdef1234567890"))
                .andExpect(jsonPath("$.shortSha").value("abcdef1"))
                .andExpect(jsonPath("$.additions").value(12))
                .andExpect(jsonPath("$.deletions").value(4))
                .andExpect(jsonPath("$.changedFilesCount").value(1))
                .andExpect(jsonPath("$.parents[0].sha").value("parent_sha"))
                .andExpect(jsonPath("$.files[0].filename").value("src/Auth.java"))
                .andExpect(jsonPath("$.files[0].status").value("modified"))
                .andExpect(jsonPath("$.files[0].patch").value("@@ -1,4 +1,12 @@"));
    }
}