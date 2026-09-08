package com.gitracker.dto;

import java.util.List;

public record GitHubParticipationDto(
        List<Integer> all,
        List<Integer> owner
) {}
