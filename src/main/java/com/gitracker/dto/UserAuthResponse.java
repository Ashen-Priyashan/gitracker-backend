package com.gitracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "Authenticated user profile state")
public record UserAuthResponse(

        @Schema(description = "Gitracker User ID")
        Long id,

        @Schema(description = "User full name")
        String name,

        @Schema(description = "User email address")
        String email,

        @Schema(description = "Indicates whether user has connected a GitHub account")
        boolean githubConnected,

        @Schema(description = "Connected GitHub username if connected")
        String githubUsername,

        @Schema(description = "Connected GitHub avatar URL if connected")
        String githubAvatarUrl,

        @Schema(description = "Account created timestamp ISO string")
        String createdAt

) implements Serializable {
}
