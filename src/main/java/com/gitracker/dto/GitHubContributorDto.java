package com.gitracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubContributorDto(
        String login,
        @JsonProperty("avatar_url")
        String avatarUrl,
        @JsonProperty("html_url")
        String htmlUrl,
        int contributions,
        String type
) {}
