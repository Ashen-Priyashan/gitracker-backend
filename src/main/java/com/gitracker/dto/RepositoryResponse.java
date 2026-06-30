package com.gitracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "RepositoryResponse",
        description = "Represents a GitHub repository."
)
public record RepositoryResponse(

        String name,
        String description,
        int stars,
        int forks,
        int watchers,
        String language,
        String createdAt,
        String updatedAt

) {
}