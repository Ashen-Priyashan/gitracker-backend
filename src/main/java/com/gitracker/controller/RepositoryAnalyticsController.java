package com.gitracker.controller;

import com.gitracker.dto.RepositoryAnalyticsResponse;
import com.gitracker.service.RepositoryAnalyticsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class RepositoryAnalyticsController {

    private final RepositoryAnalyticsService repositoryAnalyticsService;

    public RepositoryAnalyticsController(
            RepositoryAnalyticsService repositoryAnalyticsService
    ) {
        this.repositoryAnalyticsService =
                repositoryAnalyticsService;
    }

    @GetMapping("/{username}/repository-analytics")
    public RepositoryAnalyticsResponse getRepositoryAnalytics(
            @PathVariable String username
    ) {
        return repositoryAnalyticsService
                .getRepositoryAnalytics(username);
    }
}