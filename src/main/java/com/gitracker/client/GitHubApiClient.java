package com.gitracker.client;

import com.gitracker.dto.GitHubRepositoryDto;
import com.gitracker.dto.GitHubUserDto;
import com.gitracker.exception.RateLimitExceededException;
import com.gitracker.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;

@Component
public class GitHubApiClient {

    private final RestClient restClient;

    

public GitHubApiClient(
        @Value("${github.api.base-url}")
        String baseUrl
) {
    this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .build();
}

    public GitHubUserDto getUser(String username) {

        try {
            return restClient.get()
                    .uri("/users/{username}", username)
                    .retrieve()
                    .body(GitHubUserDto.class);

        } catch (HttpClientErrorException.NotFound exception) {

            throw new ResourceNotFoundException(
                    "GitHub user '" + username + "' not found"
            );

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

        } catch (HttpClientErrorException.NotFound exception) {

            throw new ResourceNotFoundException(
                    "GitHub user '" + username + "' not found"
            );

        } catch (HttpClientErrorException.Forbidden exception) {

            throw new RateLimitExceededException(
                    "GitHub API rate limit exceeded. Please try again later."
            );
        }
    }
}