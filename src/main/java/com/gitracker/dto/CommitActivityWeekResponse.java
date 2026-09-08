package com.gitracker.dto;

import java.util.List;

public record CommitActivityWeekResponse(
        long weekTimestamp,
        int totalCommits,
        List<Integer> days
) {}
