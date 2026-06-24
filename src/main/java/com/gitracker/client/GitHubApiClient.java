package com.gitracker.client;

import com.gitracker.dto.GitHubRepositoryDto;
import com.gitracker.dto.GitHubUserDto;
import com.gitracker.exception.RateLimitExceededException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class GitHubApiClient {

    private final RestClient restClient;

    public GitHubApiClient() {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.github.com")
                .build();
    }

    public GitHubUserDto getUser(String username) {

        try {
            return restClient.get()
                    .uri("/users/{username}", username)
                    .retrieve()
                    .body(GitHubUserDto.class);

        } catch (HttpClientErrorException.Forbidden exception) {

            throw new RateLimitExceededException(
                    "GitHub API rate limit exceeded. Please try again later."
            );
        }
    }

    public GitHubRepositoryDto[] getRepositories(String username) {

        try {
            return restClient.get()
                    .uri("/users/{username}/repos", username)
                    .retrieve()
                    .body(GitHubRepositoryDto[].class);

        } catch (HttpClientErrorException.Forbidden exception) {

            throw new RateLimitExceededException(
                    "GitHub API rate limit exceeded. Please try again later."
            );
        }
    }
}