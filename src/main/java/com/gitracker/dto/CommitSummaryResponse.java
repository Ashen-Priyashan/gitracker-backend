package com.gitracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "CommitSummaryResponse",
        description = "Summary of a Git commit in a repository branch history."
)
public record CommitSummaryResponse(
        String sha,
        String shortSha,
        String message,
        String authorName,
        String authorLogin,
        String authorAvatarUrl,
        String authorHtmlUrl,
        String committerName,
        String committerLogin,
        String committerAvatarUrl,
        String committerHtmlUrl,
        String authoredAt,
        String committedAt,
        String htmlUrl
) {
}
