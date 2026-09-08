package com.gitracker.dto;

import java.util.List;

public record GitHubCommitActivityDto(
        long week,
        int total,
        List<Integer> days
) {}
