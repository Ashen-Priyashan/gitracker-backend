package com.gitracker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubBranchDto(
        String name,
        @JsonProperty("protected")
        boolean isProtected,
        GitHubBranchCommitDto commit
) {
}
