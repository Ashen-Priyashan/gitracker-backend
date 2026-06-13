package com.gitracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubRepositoryDto(

        String name,
        String description,

        @JsonProperty("stargazers_count")
        int stars,

        @JsonProperty("forks_count")
        int forks,

        @JsonProperty("watchers_count")
        int watchers,

        String language,

        @JsonProperty("created_at")
        String createdAt,

        @JsonProperty("updated_at")
        String updatedAt

) {
}