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

import java.util.ArrayList;
import java.util.List;

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

    GitHubApiClient(RestClient restClient) {
        this.restClient = restClient;
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

    private static final int PER_PAGE = 100;
    private static final int MAX_PAGES = 10;

    public GitHubRepositoryDto[] getRepositories(String username) {
        List<GitHubRepositoryDto> allRepositories = new ArrayList<>();
        int page = 1;

        while (page <= MAX_PAGES) {
            try {
                GitHubRepositoryDto[] batch = restClient.get()
                        .uri("/users/{username}/repos?per_page={perPage}&page={page}&sort=updated", username, PER_PAGE, page)
                        .retrieve()
                        .body(GitHubRepositoryDto[].class);

                if (batch == null || batch.length == 0) {
                    break;
                }

                allRepositories.addAll(List.of(batch));

                if (batch.length < PER_PAGE) {
                    break;
                }

                page++;
            } catch (HttpClientErrorException.NotFound exception) {
                if (page == 1) {
                    throw new ResourceNotFoundException(
                            "GitHub user '" + username + "' not found"
                    );
                }
                break;
            } catch (HttpClientErrorException.Forbidden exception) {
                throw new RateLimitExceededException(
                        "GitHub API rate limit exceeded. Please try again later."
                );
            }
        }

        return allRepositories.toArray(new GitHubRepositoryDto[0]);
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

    public GitHubUserDto getAuthenticatedUser(String accessToken) {
        try {
            return restClient.get()
                    .uri("/user")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(GitHubUserDto.class);

        } catch (HttpClientErrorException.Unauthorized exception) {
            throw new ResourceNotFoundException("Invalid or expired GitHub access token.");

        } catch (HttpClientErrorException.Forbidden exception) {
            throw new RateLimitExceededException(
                    "GitHub API rate limit exceeded. Please try again later."
            );
        }
    }

    public GitHubRepositoryDto[] getAuthenticatedUserRepositories(String accessToken) {
        List<GitHubRepositoryDto> allRepositories = new ArrayList<>();
        int page = 1;

        while (page <= MAX_PAGES) {
            try {
                GitHubRepositoryDto[] batch = restClient.get()
                        .uri("/user/repos?type=all&sort=updated&per_page={perPage}&page={page}", PER_PAGE, page)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .retrieve()
                        .body(GitHubRepositoryDto[].class);

                if (batch == null || batch.length == 0) {
                    break;
                }

                allRepositories.addAll(List.of(batch));

                if (batch.length < PER_PAGE) {
                    break;
                }

                page++;
            } catch (HttpClientErrorException.Unauthorized exception) {
                throw new ResourceNotFoundException("Invalid or expired GitHub access token.");
            } catch (HttpClientErrorException.Forbidden exception) {
                throw new RateLimitExceededException(
                        "GitHub API rate limit exceeded. Please try again later."
                );
            }
        }

        return allRepositories.toArray(new GitHubRepositoryDto[0]);
    }
}