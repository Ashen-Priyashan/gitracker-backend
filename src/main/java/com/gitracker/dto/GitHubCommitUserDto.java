package com.gitracker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubCommitUserDto(
        String name,
        String email,
        String date
) {
}
