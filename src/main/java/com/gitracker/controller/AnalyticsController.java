package com.gitracker.controller;

import com.gitracker.entity.UserAnalytics;
import com.gitracker.service.UserAnalyticsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final UserAnalyticsService userAnalyticsService;

    public AnalyticsController(
            UserAnalyticsService userAnalyticsService
    ) {
        this.userAnalyticsService = userAnalyticsService;
    }

    @GetMapping("/{username}")
    public UserAnalytics generateAnalytics(
            @PathVariable String username
    ) {
        return userAnalyticsService.generateAnalytics(username);
    }
}