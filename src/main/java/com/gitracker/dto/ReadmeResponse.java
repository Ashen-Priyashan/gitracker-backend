package com.gitracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "ReadmeResponse",
        description = "Represents repository README content."
)
public record ReadmeResponse(
        String content,
        String htmlUrl,
        boolean exists
) {
}
