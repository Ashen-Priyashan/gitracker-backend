package com.gitracker.dto;

import java.util.List;

public record ParticipationResponse(
        String status,
        List<Integer> allCommits,
        List<Integer> ownerCommits
) {}
