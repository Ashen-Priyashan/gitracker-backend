package com.gitracker.dto;

import java.util.List;

public record CodeFrequencyResponse(
        String status,
        List<CodeFrequencyWeekResponse> weeklyStats
) {}
