package com.gitracker.controller;

import com.gitracker.dto.ProfileSummaryResponse;
import com.gitracker.service.ProfileSummaryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileSummaryService profileSummaryService;

    public ProfileController(
            ProfileSummaryService profileSummaryService
    ) {
        this.profileSummaryService = profileSummaryService;
    }

    @GetMapping("/{username}")
    public ProfileSummaryResponse getProfile(
            @PathVariable String username
    ) {
        return profileSummaryService.getProfileSummary(username);
    }
}