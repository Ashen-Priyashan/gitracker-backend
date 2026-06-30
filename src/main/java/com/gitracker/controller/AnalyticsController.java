package com.gitracker.controller;

import com.gitracker.entity.UserAnalytics;
import com.gitracker.service.UserAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "Analytics",
        description = "Operations for generating GitHub user analytics"
)
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final UserAnalyticsService userAnalyticsService;

    public AnalyticsController(
            UserAnalyticsService userAnalyticsService
    ) {
        this.userAnalyticsService = userAnalyticsService;
    }

    @Operation(
            summary = "Generate user analytics",
            description = "Generates analytics for a GitHub user based on their stored repositories, including total repositories, stars, forks, and most used programming language."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Analytics generated successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "GitHub user not found"
            )
    })
    @GetMapping("/{username}")
    public UserAnalytics generateAnalytics(
            @Parameter(
                    description = "GitHub username",
                    example = "octocat"
            )
            @PathVariable
            String username
    ) {
        return userAnalyticsService.generateAnalytics(username);
    }
}