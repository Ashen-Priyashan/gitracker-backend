package com.gitracker.service;

import com.gitracker.client.GitHubApiClient;
import com.gitracker.dto.GitHubRepositoryDto;
import com.gitracker.dto.LanguageAnalyticsResponse;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class LanguageAnalyticsService {

    private final GitHubApiClient gitHubApiClient;

    public LanguageAnalyticsService(
            GitHubApiClient gitHubApiClient
    ) {
        this.gitHubApiClient = gitHubApiClient;
    }

    public LanguageAnalyticsResponse getLanguageAnalytics(
            String username
    ) {

        GitHubRepositoryDto[] repositories =
                gitHubApiClient.getRepositories(username);

        Map<String, Integer> languageCount = new HashMap<>();

        for (GitHubRepositoryDto repository : repositories) {

            String language = repository.language();

            if (language == null || language.isBlank()) {
                language = "Unknown";
            }

            languageCount.put(
                    language,
                    languageCount.getOrDefault(language, 0) + 1
            );
        }

        int totalRepositories = repositories.length;

        Map<String, Double> breakdown = new HashMap<>();

        String mostUsedLanguage = "Unknown";
        int maxCount = 0;

        for (Map.Entry<String, Integer> entry : languageCount.entrySet()) {

            double percentage =
                    (entry.getValue() * 100.0) / totalRepositories;

            breakdown.put(
                    entry.getKey(),
                    Math.round(percentage * 100.0) / 100.0
            );

            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                mostUsedLanguage = entry.getKey();
            }
        }

        return new LanguageAnalyticsResponse(
                mostUsedLanguage,
                totalRepositories,
                breakdown
        );
    }
}