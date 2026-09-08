package com.gitracker.dto;

public record ContributorResponse(
        String login,
        String avatarUrl,
        String htmlUrl,
        int contributions,
        String type
) {}
