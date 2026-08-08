package com.gitracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GitHubRepositoryDto(

        Long id,

        String name,
        String description,

        @JsonProperty("stargazers_count")
        int stars,

        @JsonProperty("forks_count")
        int forks,

        @JsonProperty("watchers_count")
        int watchers,

        String language,

        @JsonProperty("default_branch")
        String defaultBranch,

        @JsonProperty("open_issues_count")
        int openIssues,

        GitHubLicenseDto license,

        List<String> topics,

        @JsonProperty("html_url")
        String htmlUrl,

        int size,

        boolean archived,

        @JsonProperty("created_at")
        String createdAt,

        @JsonProperty("updated_at")
        String updatedAt

) {
}