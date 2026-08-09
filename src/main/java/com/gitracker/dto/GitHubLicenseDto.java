package com.gitracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubLicenseDto(
        String key,
        String name,
        @JsonProperty("spdx_id")
        String spdxId,
        String url
) {
}
