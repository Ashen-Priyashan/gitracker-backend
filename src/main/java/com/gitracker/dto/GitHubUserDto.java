package com.gitracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubUserDto(

        Long id,

        String login,
        String name,
        String bio,
        String location,

        int followers,
        int following,

        @JsonProperty("public_repos")
        int publicRepos,

        @JsonProperty("avatar_url")
        String avatarUrl,

        @JsonProperty("created_at")
        String createdAt

) {
}