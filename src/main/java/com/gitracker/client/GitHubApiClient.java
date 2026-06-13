package com.gitracker.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GitHubApiClient {

    private final RestClient restClient;

    public GitHubApiClient() {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.github.com")
                .build();
    }

    public String getUser(String username) {
        return restClient.get()
                .uri("/users/{username}", username)
                .retrieve()
                .body(String.class);
    }
}