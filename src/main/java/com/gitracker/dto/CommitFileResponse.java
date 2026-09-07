package com.gitracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "CommitFileResponse",
        description = "File modified in a commit."
)
public record CommitFileResponse(
        String filename,
        String status,
        int additions,
        int deletions,
        int changes,
        String blobUrl,
        String rawUrl,
        String patch,
        String previousFilename
) {
}
