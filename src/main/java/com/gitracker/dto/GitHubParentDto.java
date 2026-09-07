package com.gitracker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubParentDto(
        String sha,
        String url,
        @JsonProperty("html_url")
        String htmlUrl
) {
}
