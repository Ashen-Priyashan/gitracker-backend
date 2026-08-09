package com.gitracker.dto;

import com.gitracker.entity.UserAnalytics;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(
        name = "ProfileSummaryResponse",
        description = "Represents a complete GitHub profile summary including user information, analytics, and repositories."
)
public record ProfileSummaryResponse(

        GitHubUserResponse user,
        UserAnalytics analytics,
        List<RepositoryResponse> repositories

) {
}