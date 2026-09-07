package com.gitracker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubCommitAuthorDto(
        Long id,
        String login,
        @JsonProperty("avatar_url")
        String avatarUrl,
        @JsonProperty("html_url")
        String htmlUrl
) {
}
