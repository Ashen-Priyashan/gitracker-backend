package com.gitracker.dto;

public record GitHubUserResponse(
        String username,
        String name,
        String bio,
        String location,
        int followers,
        int following,
        String avatarUrl,
        String createdAt
) {
}