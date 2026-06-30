package com.gitracker.controller;

import com.gitracker.dto.GitHubUserResponse;
import com.gitracker.service.GitHubUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "Users",
        description = "Operations for retrieving GitHub user profiles"
)
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final GitHubUserService gitHubUserService;

    public UserController(GitHubUserService gitHubUserService) {
        this.gitHubUserService = gitHubUserService;
    }

    @Operation(
            summary = "Get GitHub user profile",
            description = "Retrieves a GitHub user's profile information from the GitHub API and stores it locally if it does not already exist."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "User retrieved successfully"
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
    @GetMapping("/{username}")
    public GitHubUserResponse getUser(
            @Parameter(
                    description = "GitHub username",
                    example = "octocat"
            )
            @PathVariable
            String username
    ) {
        return gitHubUserService.getUser(username);
    }
}