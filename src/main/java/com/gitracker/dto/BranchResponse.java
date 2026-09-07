package com.gitracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "BranchResponse",
        description = "Represents a Git branch in a repository."
)
public record BranchResponse(
        String name,
        boolean isProtected,
        String commitSha
) {
}
