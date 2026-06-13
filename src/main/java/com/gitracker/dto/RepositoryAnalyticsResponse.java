package com.gitracker.dto;

public record RepositoryAnalyticsResponse(
        int totalRepositories,
        int totalStars,
        int totalForks,
        int totalWatchers,
        String mostStarredRepository,
        String mostForkedRepository,
        String mostWatchedRepository
) {
}