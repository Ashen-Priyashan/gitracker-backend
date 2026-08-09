package com.gitracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
        name = "RepositoryResponse",
        description = "Represents a GitHub repository with metadata and insights."
)
public record RepositoryResponse(

        String name,
        String description,
        int stars,
        int forks,
        int watchers,
        String language,
        String defaultBranch,
        int openIssues,
        String licenseName,
        List<String> topics,
        String htmlUrl,
        int size,
        boolean archived,
        String createdAt,
        String updatedAt

) {
}