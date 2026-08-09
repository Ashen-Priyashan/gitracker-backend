package com.gitracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(
        name = "GitHubUserResponse",
        description = "Represents a GitHub user's profile information."
)
public record GitHubUserResponse(

        String username,
        String name,
        String bio,
        String location,
        int followers,
        int following,
        String avatarUrl,
        String createdAt

) implements Serializable {
}