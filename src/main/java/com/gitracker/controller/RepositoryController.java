package com.gitracker.controller;

import com.gitracker.dto.*;
import com.gitracker.service.GitHubRepositoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(
        name = "Repositories",
        description = "Operations for retrieving GitHub repositories, branches, commits, and repository metadata"
)
@RestController
@RequestMapping("/api/users")
public class RepositoryController {

    private final GitHubRepositoryService repositoryService;

    public RepositoryController(
            GitHubRepositoryService repositoryService
    ) {
        this.repositoryService = repositoryService;
    }

    @Operation(
            summary = "Get user repositories",
            description = "Retrieves all public repositories for a GitHub user and stores new repositories locally if they do not already exist."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Repositories retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "GitHub user not found"
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "GitHub API rate limit exceeded"
            )
    })
    @GetMapping("/{username}/repositories")
    public List<RepositoryResponse> getRepositories(
            @Parameter(
                    description = "GitHub username",
                    example = "octocat"
            )
            @PathVariable
            String username
    ) {

        return repositoryService.getRepositories(username);
    }

    @Operation(
            summary = "Get repository details",
            description = "Retrieves details for a specific repository belonging to a GitHub user."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Repository details retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Repository or user not found"
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "GitHub API rate limit exceeded"
            )
    })
    @GetMapping("/{username}/repositories/{repoName}")
    public RepositoryResponse getRepository(
            @Parameter(description = "GitHub username", example = "octocat")
            @PathVariable String username,
            @Parameter(description = "Repository name", example = "Hello-World")
            @PathVariable String repoName
    ) {
        return repositoryService.getRepository(username, repoName);
    }

    @Operation(
            summary = "Get repository README",
            description = "Retrieves the README file content for a specific GitHub repository."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "README retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "README or Repository not found"
            )
    })
    @GetMapping("/{username}/repositories/{repoName}/readme")
    public ReadmeResponse getReadme(
            @Parameter(description = "GitHub username", example = "octocat")
            @PathVariable String username,
            @Parameter(description = "Repository name", example = "Hello-World")
            @PathVariable String repoName
    ) {
        return repositoryService.getReadme(username, repoName);
    }

    @Operation(
            summary = "Get repository branches",
            description = "Retrieves all branches for a specific GitHub repository."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Branches retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Repository or user not found"
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "GitHub API rate limit exceeded"
            )
    })
    @GetMapping("/{username}/repositories/{repoName}/branches")
    public List<BranchResponse> getBranches(
            @Parameter(description = "GitHub username", example = "octocat")
            @PathVariable String username,
            @Parameter(description = "Repository name", example = "Hello-World")
            @PathVariable String repoName
    ) {
        return repositoryService.getBranches(username, repoName);
    }

    @Operation(
            summary = "Get branch commits",
            description = "Retrieves paginated commit history for a specific branch of a GitHub repository."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Commits retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Repository, branch, or user not found"
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "GitHub API rate limit exceeded"
            )
    })
    @GetMapping("/{username}/repositories/{repoName}/commits")
    public List<CommitSummaryResponse> getCommits(
            @Parameter(description = "GitHub username", example = "octocat")
            @PathVariable String username,
            @Parameter(description = "Repository name", example = "Hello-World")
            @PathVariable String repoName,
            @Parameter(description = "Branch name or commit SHA to start listing commits from", example = "main")
            @RequestParam(required = false) String branch,
            @Parameter(description = "Page number (1-based)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Number of commits per page", example = "20")
            @RequestParam(defaultValue = "20") int perPage
    ) {
        return repositoryService.getCommits(username, repoName, branch, page, perPage);
    }

    @Operation(
            summary = "Get commit detail",
            description = "Retrieves detailed information for a specific commit including stats, parents, and changed file diffs."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Commit details retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Commit, repository, or user not found"
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "GitHub API rate limit exceeded"
            )
    })
    @GetMapping("/{username}/repositories/{repoName}/commits/{sha}")
    public CommitDetailResponse getCommit(
            @Parameter(description = "GitHub username", example = "octocat")
            @PathVariable String username,
            @Parameter(description = "Repository name", example = "Hello-World")
            @PathVariable String repoName,
            @Parameter(description = "Commit SHA", example = "6dcb09b5b57875f334f61aebed695e2e4193db5e")
            @PathVariable String sha
    ) {
        return repositoryService.getCommit(username, repoName, sha);
    }

    @Operation(
            summary = "Get repository contributors",
            description = "Retrieves the list of contributors and their contribution counts for a specific repository."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Contributors retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Repository or user not found"
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "GitHub API rate limit exceeded"
            )
    })
    @GetMapping("/{username}/repositories/{repoName}/contributors")
    public List<ContributorResponse> getContributors(
            @Parameter(description = "GitHub username", example = "octocat")
            @PathVariable String username,
            @Parameter(description = "Repository name", example = "Hello-World")
            @PathVariable String repoName
    ) {
        return repositoryService.getContributors(username, repoName);
    }

    @Operation(
            summary = "Get commit activity statistics",
            description = "Retrieves 52-week commit activity history for a specific repository."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Commit activity statistics retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Repository or user not found"
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "GitHub API rate limit exceeded"
            )
    })
    @GetMapping("/{username}/repositories/{repoName}/stats/commit-activity")
    public CommitActivityResponse getCommitActivity(
            @Parameter(description = "GitHub username", example = "octocat")
            @PathVariable String username,
            @Parameter(description = "Repository name", example = "Hello-World")
            @PathVariable String repoName
    ) {
        return repositoryService.getCommitActivity(username, repoName);
    }

    @Operation(
            summary = "Get code frequency statistics",
            description = "Retrieves weekly additions and deletions for a specific repository."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Code frequency statistics retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Repository or user not found"
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "GitHub API rate limit exceeded"
            )
    })
    @GetMapping("/{username}/repositories/{repoName}/stats/code-frequency")
    public CodeFrequencyResponse getCodeFrequency(
            @Parameter(description = "GitHub username", example = "octocat")
            @PathVariable String username,
            @Parameter(description = "Repository name", example = "Hello-World")
            @PathVariable String repoName
    ) {
        return repositoryService.getCodeFrequency(username, repoName);
    }

    @Operation(
            summary = "Get participation statistics",
            description = "Retrieves weekly commit count split between the repository owner and all contributors."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Participation statistics retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Repository or user not found"
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "GitHub API rate limit exceeded"
            )
    })
    @GetMapping("/{username}/repositories/{repoName}/stats/participation")
    public ParticipationResponse getParticipation(
            @Parameter(description = "GitHub username", example = "octocat")
            @PathVariable String username,
            @Parameter(description = "Repository name", example = "Hello-World")
            @PathVariable String repoName
    ) {
        return repositoryService.getParticipation(username, repoName);
    }
}