package com.gitracker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubCommitStatsDto(
        int total,
        int additions,
        int deletions
) {
}
