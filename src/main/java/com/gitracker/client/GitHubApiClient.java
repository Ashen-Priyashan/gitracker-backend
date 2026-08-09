package com.gitracker.client;

import com.gitracker.dto.GitHubReadmeDto;
import com.gitracker.dto.GitHubRepositoryDto;
import com.gitracker.dto.GitHubUserDto;
import com.gitracker.exception.RateLimitExceededException;
import com.gitracker.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class GitHubApiClient {

    private final RestClient restClient;

    public GitHubApiClient(
            @Value("${github.api.base-url}")
            String baseUrl,
            @Value("${github.api.token:}")
            String token
    ) {

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl);

        if (token != null && !token.isBlank()) {
            builder.defaultHeader(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + token
            );
        }

        this.restClient = builder.build();
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

    public GitHubReadmeDto getReadme(String username, String repoName) {

        try {
            return restClient.get()
                    .uri("/repos/{username}/{repoName}/readme", username, repoName)
                    .retrieve()
                    .body(GitHubReadmeDto.class);

        } catch (HttpClientErrorException.NotFound exception) {
            return null;

        } catch (HttpClientErrorException.Forbidden exception) {
            throw new RateLimitExceededException(
                    "GitHub API rate limit exceeded. Please try again later."
            );
        }
    }
}