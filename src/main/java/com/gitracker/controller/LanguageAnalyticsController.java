package com.gitracker.controller;

import com.gitracker.dto.LanguageAnalyticsResponse;
import com.gitracker.service.LanguageAnalyticsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class LanguageAnalyticsController {

    private final LanguageAnalyticsService languageAnalyticsService;

    public LanguageAnalyticsController(
            LanguageAnalyticsService languageAnalyticsService
    ) {
        this.languageAnalyticsService = languageAnalyticsService;
    }

    @GetMapping("/{username}/language-analytics")
    public LanguageAnalyticsResponse getLanguageAnalytics(
            @PathVariable String username
    ) {
        return languageAnalyticsService.getLanguageAnalytics(username);
    }
}