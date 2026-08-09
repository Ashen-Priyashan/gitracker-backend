package com.gitracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubReadmeDto(
        String name,
        String path,
        String encoding,
        String content,
        @JsonProperty("html_url")
        String htmlUrl
) {
}
