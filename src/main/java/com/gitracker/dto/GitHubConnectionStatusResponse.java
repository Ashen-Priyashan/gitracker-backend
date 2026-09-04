package com.gitracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(
        name = "GitHubConnectionStatusResponse",
        description = "Represents the status of GitHub account connection."
)
public record GitHubConnectionStatusResponse(

        @Schema(description = "Indicates if a GitHub account is connected")
        boolean connected,

        @Schema(description = "Connected GitHub username if authenticated", example = "octocat")
        String username,

        @Schema(description = "Connected GitHub user avatar URL")
        String avatarUrl,

        @Schema(description = "Connected timestamp ISO string")
        String connectedAt,

        @Schema(description = "Last synchronization timestamp ISO string")
        String lastSyncedAt

) implements Serializable {

    public GitHubConnectionStatusResponse(boolean connected, String username, String avatarUrl, String connectedAt) {
        this(connected, username, avatarUrl, connectedAt, null);
    }
}
