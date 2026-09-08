package com.gitracker.dto;

import java.util.List;

public record CommitActivityResponse(
        String status,
        int totalYearCommits,
        List<CommitActivityWeekResponse> weeks
) {}
