package com.gitracker.dto;

public record CodeFrequencyWeekResponse(
        long weekTimestamp,
        int additions,
        int deletions,
        int netChange
) {}
