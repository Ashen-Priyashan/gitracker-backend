package com.gitracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubUserDto(

        String login,
        String name,
        String bio,
        String location,

        int followers,
        int following,

        @JsonProperty("avatar_url")
        String avatarUrl,

        @JsonProperty("created_at")
        String createdAt

) {
}