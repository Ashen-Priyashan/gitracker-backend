package com.gitracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "CommitParentResponse",
        description = "Parent commit reference."
)
public record CommitParentResponse(
        String sha,
        String htmlUrl
) {
}
