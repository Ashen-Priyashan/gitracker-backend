package com.gitracker.controller;

import com.gitracker.dto.ReadmeResponse;
import com.gitracker.dto.RepositoryResponse;
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
        description = "Operations for retrieving GitHub repositories and repository metadata"
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
}