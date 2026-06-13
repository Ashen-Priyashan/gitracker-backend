package com.gitracker.dto;

import java.util.Map;

public record LanguageAnalyticsResponse(
        String mostUsedLanguage,
        int totalRepositories,
        Map<String, Double> languageBreakdown
) {
}