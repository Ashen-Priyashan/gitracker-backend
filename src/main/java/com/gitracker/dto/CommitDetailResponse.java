package com.gitracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
        name = "CommitDetailResponse",
        description = "Full details of a specific Git commit including statistics, parents, and file diffs."
)
public record CommitDetailResponse(
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
        String htmlUrl,
        int additions,
        int deletions,
        int totalChanges,
        int changedFilesCount,
        List<CommitParentResponse> parents,
        List<CommitFileResponse> files
) {
}
