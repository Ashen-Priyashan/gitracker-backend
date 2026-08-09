package com.gitracker.service;

import com.gitracker.client.GitHubApiClient;
import com.gitracker.dto.GitHubRepositoryDto;
import com.gitracker.dto.RepositoryAnalyticsResponse;
import org.springframework.stereotype.Service;

@Service
public class RepositoryAnalyticsService {

    private final GitHubApiClient gitHubApiClient;

    public RepositoryAnalyticsService(
            GitHubApiClient gitHubApiClient
    ) {
        this.gitHubApiClient = gitHubApiClient;
    }

    public RepositoryAnalyticsResponse getRepositoryAnalytics(
            String username
    ) {

        GitHubRepositoryDto[] repositories =
                gitHubApiClient.getRepositories(username);

        int totalStars = 0;
        int totalForks = 0;
        int totalWatchers = 0;

        String mostStarredRepo = "N/A";
        String mostForkedRepo = "N/A";
        String mostWatchedRepo = "N/A";

        int maxStars = -1;
        int maxForks = -1;
        int maxWatchers = -1;

        for (GitHubRepositoryDto repository : repositories) {

            totalStars += repository.stars();
            totalForks += repository.forks();
            totalWatchers += repository.watchers();

            if (repository.stars() > maxStars) {
                maxStars = repository.stars();
                mostStarredRepo = repository.name();
            }

            if (repository.forks() > maxForks) {
                maxForks = repository.forks();
                mostForkedRepo = repository.name();
            }

            if (repository.watchers() > maxWatchers) {
                maxWatchers = repository.watchers();
                mostWatchedRepo = repository.name();
            }
        }

        return new RepositoryAnalyticsResponse(
                repositories.length,
                totalStars,
                totalForks,
                totalWatchers,
                mostStarredRepo,
                mostForkedRepo,
                mostWatchedRepo
        );
    }
}