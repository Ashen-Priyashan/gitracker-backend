package com.gitracker.dto;

import com.gitracker.entity.UserAnalytics;

import java.util.List;

public record ProfileSummaryResponse(
        GitHubUserResponse user,
        UserAnalytics analytics,
        List<RepositoryResponse> repositories
) {
}