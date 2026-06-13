package com.gitracker.dto;

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