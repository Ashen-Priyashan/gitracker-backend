package com.gitracker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubCommitDetailDto(
        String sha,
        @JsonProperty("node_id")
        String nodeId,
        GitHubGitCommitDto commit,
        @JsonProperty("html_url")
        String htmlUrl,
        GitHubCommitAuthorDto author,
        GitHubCommitAuthorDto committer,
        GitHubCommitStatsDto stats,
        List<GitHubCommitFileDto> files,
        List<GitHubParentDto> parents
) {
}
