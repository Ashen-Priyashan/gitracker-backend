package com.gitracker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubCommitFileDto(
        String sha,
        String filename,
        String status,
        int additions,
        int deletions,
        int changes,
        @JsonProperty("blob_url")
        String blobUrl,
        @JsonProperty("raw_url")
        String rawUrl,
        @JsonProperty("contents_url")
        String contentsUrl,
        String patch,
        @JsonProperty("previous_filename")
        String previousFilename
) {
}
