package com.gitracker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubGitCommitDto(
        GitHubCommitUserDto author,
        GitHubCommitUserDto committer,
        String message
) {
}
