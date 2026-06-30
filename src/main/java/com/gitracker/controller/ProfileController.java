package com.gitracker.controller;

import com.gitracker.dto.ProfileSummaryResponse;
import com.gitracker.service.ProfileSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "Profile Summary",
        description = "Operations for retrieving a complete GitHub user profile summary"
)
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileSummaryService profileSummaryService;

    public ProfileController(
            ProfileSummaryService profileSummaryService
    ) {
        this.profileSummaryService = profileSummaryService;
    }

    @Operation(
            summary = "Get complete profile summary",
            description = "Retrieves a complete GitHub profile summary, including user information, repository details, and generated analytics."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Profile summary retrieved successfully"
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
    public ProfileSummaryResponse getProfile(
            @Parameter(
                    description = "GitHub username",
                    example = "octocat"
            )
            @PathVariable
            String username
    ) {
        return profileSummaryService.getProfileSummary(username);
    }
}