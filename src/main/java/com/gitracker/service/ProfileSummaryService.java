package com.gitracker.service;

import com.gitracker.dto.GitHubUserResponse;
import com.gitracker.dto.ProfileSummaryResponse;
import com.gitracker.dto.RepositoryResponse;
import com.gitracker.entity.UserAnalytics;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProfileSummaryService {

    private final GitHubUserService gitHubUserService;
    private final GitHubRepositoryService gitHubRepositoryService;
    private final UserAnalyticsService userAnalyticsService;

    public ProfileSummaryService(
            GitHubUserService gitHubUserService,
            GitHubRepositoryService gitHubRepositoryService,
            UserAnalyticsService userAnalyticsService
    ) {
        this.gitHubUserService = gitHubUserService;
        this.gitHubRepositoryService = gitHubRepositoryService;
        this.userAnalyticsService = userAnalyticsService;
    }

    public ProfileSummaryResponse getProfileSummary(
            String username
    ) {

        GitHubUserResponse user =
                gitHubUserService.getUser(username);

        List<RepositoryResponse> repositories =
                gitHubRepositoryService.getRepositories(username);

        UserAnalytics analytics =
                userAnalyticsService.generateAnalytics(username);

        return new ProfileSummaryResponse(
                user,
                analytics,
                repositories
        );
    }
}