package com.gitracker.client;

import com.gitracker.dto.*;
import com.gitracker.exception.RateLimitExceededException;
import com.gitracker.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class GitHubApiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GitHubApiClient(
            @Value("${github.api.base-url}")
            String baseUrl,
            @Value("${github.api.token:}")
            String token,
            @Value("${github.api.connect-timeout-seconds:5}")
            int connectTimeoutSeconds,
            @Value("${github.api.read-timeout-seconds:10}")
            int readTimeoutSeconds,
            @Autowired(required = false)
            ObjectMapper objectMapper
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl);

        if (token != null && !token.isBlank()) {
            builder.defaultHeader(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + token
            );
        }

        this.restClient = builder.build();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    GitHubApiClient(RestClient restClient) {
        this.restClient = restClient;
        this.objectMapper = new ObjectMapper();
    }

    public GitHubApiClient(
            String baseUrl,
            String token
    ) {
        this(baseUrl, token, 5, 10, new ObjectMapper());
    }

    public GitHubUserDto getUser(String username) {
        try {
            return restClient.get()
                    .uri("/users/{username}", username)
                    .retrieve()
                    .body(GitHubUserDto.class);
        } catch (HttpClientErrorException exception) {
            throw handleHttpClientError(exception, "GitHub user '" + username + "'");
        } catch (Exception exception) {
            throw handleGeneralException(exception, "GitHub user '" + username + "'");
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
                    throw handleHttpClientError(exception, "GitHub user '" + username + "'");
                }
                break;
            } catch (HttpClientErrorException exception) {
                throw handleHttpClientError(exception, "repositories for user '" + username + "'");
            } catch (Exception exception) {
                throw handleGeneralException(exception, "repositories for user '" + username + "'");
            }
        }

        return allRepositories.toArray(new GitHubRepositoryDto[0]);
    }

    public GitHubRepositoryDto getRepository(String owner, String repo) {
        try {
            return restClient.get()
                    .uri("/repos/{owner}/{repo}", owner, repo)
                    .retrieve()
                    .body(GitHubRepositoryDto.class);
        } catch (HttpClientErrorException exception) {
            throw handleHttpClientError(exception, "Repository '" + repo + "' for owner '" + owner + "'");
        } catch (Exception exception) {
            throw handleGeneralException(exception, "Repository '" + repo + "' for owner '" + owner + "'");
        }
    }

    public GitHubReadmeDto getReadme(String username, String repoName) {
        try {
            return restClient.get()
                    .uri("/repos/{username}/{repoName}/readme", username, repoName)
                    .retrieve()
                    .body(GitHubReadmeDto.class);
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Conflict exception) {
            return null;
        } catch (HttpClientErrorException exception) {
            throw handleHttpClientError(exception, "README for repository '" + repoName + "'");
        } catch (Exception exception) {
            throw handleGeneralException(exception, "README for repository '" + repoName + "'");
        }
    }

    public GitHubUserDto getAuthenticatedUser(String accessToken) {
        try {
            return restClient.get()
                    .uri("/user")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(GitHubUserDto.class);
        } catch (HttpClientErrorException exception) {
            throw handleHttpClientError(exception, "authenticated user");
        } catch (Exception exception) {
            throw handleGeneralException(exception, "authenticated user");
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
            } catch (HttpClientErrorException exception) {
                throw handleHttpClientError(exception, "authenticated user repositories");
            } catch (Exception exception) {
                throw handleGeneralException(exception, "authenticated user repositories");
            }
        }

        return allRepositories.toArray(new GitHubRepositoryDto[0]);
    }

    public GitHubBranchDto[] getBranches(String owner, String repo) {
        List<GitHubBranchDto> allBranches = new ArrayList<>();
        int page = 1;

        while (page <= MAX_PAGES) {
            try {
                GitHubBranchDto[] batch = restClient.get()
                        .uri("/repos/{owner}/{repo}/branches?per_page={perPage}&page={page}", owner, repo, PER_PAGE, page)
                        .retrieve()
                        .body(GitHubBranchDto[].class);

                if (batch == null || batch.length == 0) {
                    break;
                }

                allBranches.addAll(List.of(batch));

                if (batch.length < PER_PAGE) {
                    break;
                }

                page++;
            } catch (HttpClientErrorException.NotFound exception) {
                if (page == 1) {
                    throw handleHttpClientError(exception, "Repository '" + repo + "' for owner '" + owner + "'");
                }
                break;
            } catch (HttpClientErrorException exception) {
                throw handleHttpClientError(exception, "branches for repository '" + repo + "'");
            } catch (Exception exception) {
                throw handleGeneralException(exception, "branches for repository '" + repo + "'");
            }
        }

        return allBranches.toArray(new GitHubBranchDto[0]);
    }

    public GitHubCommitListDto[] getCommits(String owner, String repo, String branch, int page, int perPage) {
        int safePage = Math.max(1, page);
        int safePerPage = perPage > 0 ? Math.min(100, perPage) : 20;

        try {
            String uri;
            Object[] uriParams;
            if (branch != null && !branch.isBlank()) {
                uri = "/repos/{owner}/{repo}/commits?sha={branch}&page={page}&per_page={perPage}";
                uriParams = new Object[]{owner, repo, branch, safePage, safePerPage};
            } else {
                uri = "/repos/{owner}/{repo}/commits?page={page}&per_page={perPage}";
                uriParams = new Object[]{owner, repo, safePage, safePerPage};
            }

            GitHubCommitListDto[] commits = restClient.get()
                    .uri(uri, uriParams)
                    .retrieve()
                    .body(GitHubCommitListDto[].class);

            return commits != null ? commits : new GitHubCommitListDto[0];
        } catch (HttpClientErrorException.NotFound exception) {
            if (safePage == 1) {
                throw handleHttpClientError(exception, "Repository '" + repo + "' or branch '" + (branch != null ? branch : "") + "' for owner '" + owner + "'");
            }
            return new GitHubCommitListDto[0];
        } catch (HttpClientErrorException.Conflict exception) {
            // Empty git repository returns 409 Conflict
            return new GitHubCommitListDto[0];
        } catch (HttpClientErrorException exception) {
            throw handleHttpClientError(exception, "commits for repository '" + repo + "'");
        } catch (Exception exception) {
            throw handleGeneralException(exception, "commits for repository '" + repo + "'");
        }
    }

    public GitHubCommitDetailDto getCommit(String owner, String repo, String sha) {
        try {
            return restClient.get()
                    .uri("/repos/{owner}/{repo}/commits/{sha}", owner, repo, sha)
                    .retrieve()
                    .body(GitHubCommitDetailDto.class);
        } catch (HttpClientErrorException exception) {
            throw handleHttpClientError(exception, "Commit '" + sha + "' in repository '" + repo + "' for owner '" + owner + "'");
        } catch (Exception exception) {
            throw handleGeneralException(exception, "Commit '" + sha + "' in repository '" + repo + "' for owner '" + owner + "'");
        }
    }

    @SuppressWarnings("rawtypes")
    public List<Map> getRepoCommits(String owner, String repo, String accessToken) {
        try {
            Map[] commits = restClient.get()
                    .uri("/repos/{owner}/{repo}/commits?per_page=30", owner, repo)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(Map[].class);
            return commits != null ? List.of(commits) : List.of();
        } catch (HttpClientErrorException.Conflict exception) {
            return List.of();
        } catch (HttpClientErrorException exception) {
            throw handleHttpClientError(exception, "commits for repository '" + repo + "'");
        } catch (Exception exception) {
            throw handleGeneralException(exception, "commits for repository '" + repo + "'");
        }
    }

    @SuppressWarnings("rawtypes")
    public List<Map> getRepoPullRequests(String owner, String repo, String accessToken) {
        try {
            Map[] prs = restClient.get()
                    .uri("/repos/{owner}/{repo}/pulls?state=all&per_page=30", owner, repo)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(Map[].class);
            return prs != null ? List.of(prs) : List.of();
        } catch (HttpClientErrorException.Conflict exception) {
            return List.of();
        } catch (HttpClientErrorException exception) {
            throw handleHttpClientError(exception, "pull requests for repository '" + repo + "'");
        } catch (Exception exception) {
            throw handleGeneralException(exception, "pull requests for repository '" + repo + "'");
        }
    }

    @SuppressWarnings("rawtypes")
    public List<Map> getRepoIssues(String owner, String repo, String accessToken) {
        try {
            Map[] issues = restClient.get()
                    .uri("/repos/{owner}/{repo}/issues?state=all&per_page=30", owner, repo)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(Map[].class);
            return issues != null ? List.of(issues) : List.of();
        } catch (HttpClientErrorException.Conflict exception) {
            return List.of();
        } catch (HttpClientErrorException exception) {
            throw handleHttpClientError(exception, "issues for repository '" + repo + "'");
        } catch (Exception exception) {
            throw handleGeneralException(exception, "issues for repository '" + repo + "'");
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Number> getRepoLanguages(String owner, String repo, String accessToken) {
        try {
            return restClient.get()
                    .uri("/repos/{owner}/{repo}/languages", owner, repo)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
        } catch (HttpClientErrorException.Conflict exception) {
            return Map.of();
        } catch (HttpClientErrorException exception) {
            throw handleHttpClientError(exception, "languages for repository '" + repo + "'");
        } catch (Exception exception) {
            throw handleGeneralException(exception, "languages for repository '" + repo + "'");
        }
    }

    public GitHubContributorDto[] getContributors(String owner, String repo) {
        try {
            ResponseEntity<String> response = restClient.get()
                    .uri("/repos/{owner}/{repo}/contributors?per_page=100", owner, repo)
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode() == HttpStatus.ACCEPTED || response.getStatusCode() == HttpStatus.NO_CONTENT || response.getBody() == null) {
                return new GitHubContributorDto[0];
            }

            String body = response.getBody().trim();
            if (body.isEmpty() || body.equals("{}") || !body.startsWith("[")) {
                return new GitHubContributorDto[0];
            }

            return objectMapper.readValue(body, GitHubContributorDto[].class);
        } catch (HttpClientErrorException.Conflict exception) {
            return new GitHubContributorDto[0];
        } catch (HttpClientErrorException exception) {
            throw handleHttpClientError(exception, "Repository '" + repo + "' for owner '" + owner + "'");
        } catch (ResourceNotFoundException | RateLimitExceededException | com.gitracker.exception.GitHubForbiddenException | com.gitracker.exception.GitHubAuthenticationException | com.gitracker.exception.GitHubValidationException | com.gitracker.exception.GitHubServiceUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw handleGeneralException(exception, "contributors for repository '" + repo + "' for owner '" + owner + "'");
        }
    }

    public GitHubCommitActivityDto[] getCommitActivity(String owner, String repo) {
        try {
            ResponseEntity<String> response = restClient.get()
                    .uri("/repos/{owner}/{repo}/stats/commit_activity", owner, repo)
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode() == HttpStatus.ACCEPTED || response.getStatusCode() == HttpStatus.NO_CONTENT || response.getBody() == null) {
                return null;
            }

            String body = response.getBody().trim();
            if (body.isEmpty() || body.equals("{}") || !body.startsWith("[")) {
                return null;
            }

            return objectMapper.readValue(body, GitHubCommitActivityDto[].class);
        } catch (HttpClientErrorException.Conflict exception) {
            return new GitHubCommitActivityDto[0];
        } catch (HttpClientErrorException exception) {
            throw handleHttpClientError(exception, "Repository '" + repo + "' for owner '" + owner + "'");
        } catch (ResourceNotFoundException | RateLimitExceededException | com.gitracker.exception.GitHubForbiddenException | com.gitracker.exception.GitHubAuthenticationException | com.gitracker.exception.GitHubValidationException | com.gitracker.exception.GitHubServiceUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw handleGeneralException(exception, "commit activity for repository '" + repo + "' for owner '" + owner + "'");
        }
    }

    public Number[][] getCodeFrequency(String owner, String repo) {
        try {
            ResponseEntity<String> response = restClient.get()
                    .uri("/repos/{owner}/{repo}/stats/code_frequency", owner, repo)
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode() == HttpStatus.ACCEPTED || response.getStatusCode() == HttpStatus.NO_CONTENT || response.getBody() == null) {
                return null;
            }

            String body = response.getBody().trim();
            if (body.isEmpty() || body.equals("{}") || !body.startsWith("[")) {
                return null;
            }

            return objectMapper.readValue(body, Number[][].class);
        } catch (HttpClientErrorException.Conflict exception) {
            return new Number[0][0];
        } catch (HttpClientErrorException exception) {
            throw handleHttpClientError(exception, "Repository '" + repo + "' for owner '" + owner + "'");
        } catch (ResourceNotFoundException | RateLimitExceededException | com.gitracker.exception.GitHubForbiddenException | com.gitracker.exception.GitHubAuthenticationException | com.gitracker.exception.GitHubValidationException | com.gitracker.exception.GitHubServiceUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw handleGeneralException(exception, "code frequency for repository '" + repo + "' for owner '" + owner + "'");
        }
    }

    public GitHubParticipationDto getParticipation(String owner, String repo) {
        try {
            ResponseEntity<String> response = restClient.get()
                    .uri("/repos/{owner}/{repo}/stats/participation", owner, repo)
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode() == HttpStatus.ACCEPTED || response.getStatusCode() == HttpStatus.NO_CONTENT || response.getBody() == null) {
                return null;
            }

            String body = response.getBody().trim();
            if (body.isEmpty() || body.equals("{}")) {
                return null;
            }

            return objectMapper.readValue(body, GitHubParticipationDto.class);
        } catch (HttpClientErrorException.Conflict exception) {
            return new GitHubParticipationDto(List.of(), List.of());
        } catch (HttpClientErrorException exception) {
            throw handleHttpClientError(exception, "Repository '" + repo + "' for owner '" + owner + "'");
        } catch (ResourceNotFoundException | RateLimitExceededException | com.gitracker.exception.GitHubForbiddenException | com.gitracker.exception.GitHubAuthenticationException | com.gitracker.exception.GitHubValidationException | com.gitracker.exception.GitHubServiceUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw handleGeneralException(exception, "participation for repository '" + repo + "' for owner '" + owner + "'");
        }
    }

    private RuntimeException handleHttpClientError(HttpClientErrorException exception, String context) {
        HttpHeaders headers = exception.getResponseHeaders();
        int statusCode = exception.getStatusCode().value();

        if (statusCode == 429) {
            Long retryAfter = extractRetryAfter(headers);
            Long resetTime = extractRateLimitReset(headers);
            return new RateLimitExceededException("GitHub API rate limit exceeded. Please try again later.", retryAfter, resetTime);
        }

        if (statusCode == 403) {
            if (isRateLimitResponse(exception)) {
                Long retryAfter = extractRetryAfter(headers);
                Long resetTime = extractRateLimitReset(headers);
                return new RateLimitExceededException("GitHub API rate limit exceeded. Please try again later.", retryAfter, resetTime);
            }
            return new com.gitracker.exception.GitHubForbiddenException("GitHub access to this resource is currently unavailable.");
        }

        if (statusCode == 404) {
            return new ResourceNotFoundException(context + " not found");
        }

        if (statusCode == 401) {
            return new com.gitracker.exception.GitHubAuthenticationException("Invalid or expired GitHub access token.");
        }

        if (statusCode == 422) {
            return new com.gitracker.exception.GitHubValidationException("GitHub API unprocessable entity for " + context + ".");
        }

        if (statusCode >= 500) {
            return new com.gitracker.exception.GitHubServiceUnavailableException("GitHub is temporarily unavailable. Please try again shortly.");
        }

        return new com.gitracker.exception.GitHubServiceUnavailableException("GitHub is temporarily unavailable. Please try again shortly.", exception);
    }

    private RuntimeException handleGeneralException(Exception exception, String context) {
        if (exception instanceof HttpClientErrorException httpError) {
            return handleHttpClientError(httpError, context);
        }
        if (exception instanceof org.springframework.web.client.HttpStatusCodeException statusError) {
            HttpHeaders headers = statusError.getResponseHeaders();
            int statusCode = statusError.getStatusCode().value();
            if (statusCode == 429) {
                return new RateLimitExceededException("GitHub API rate limit exceeded. Please try again later.", extractRetryAfter(headers), extractRateLimitReset(headers));
            }
            if (statusCode == 403) {
                if (statusError instanceof HttpClientErrorException clientError && isRateLimitResponse(clientError)) {
                    return new RateLimitExceededException("GitHub API rate limit exceeded. Please try again later.", extractRetryAfter(headers), extractRateLimitReset(headers));
                }
                return new com.gitracker.exception.GitHubForbiddenException("GitHub access to this resource is currently unavailable.");
            }
            if (statusCode == 404) {
                return new ResourceNotFoundException(context + " not found");
            }
            if (statusCode == 401) {
                return new com.gitracker.exception.GitHubAuthenticationException("Invalid or expired GitHub access token.");
            }
            if (statusCode == 422) {
                return new com.gitracker.exception.GitHubValidationException("GitHub API unprocessable entity for " + context + ".");
            }
            if (statusCode >= 500) {
                return new com.gitracker.exception.GitHubServiceUnavailableException("GitHub is temporarily unavailable. Please try again shortly.");
            }
        }
        if (exception instanceof org.springframework.web.client.ResourceAccessException) {
            return new com.gitracker.exception.GitHubServiceUnavailableException("GitHub is temporarily unavailable. Please try again shortly.", exception);
        }
        if (exception instanceof ResourceNotFoundException || exception instanceof RateLimitExceededException
                || exception instanceof com.gitracker.exception.GitHubForbiddenException
                || exception instanceof com.gitracker.exception.GitHubAuthenticationException
                || exception instanceof com.gitracker.exception.GitHubValidationException
                || exception instanceof com.gitracker.exception.GitHubServiceUnavailableException) {
            return (RuntimeException) exception;
        }
        return new com.gitracker.exception.GitHubServiceUnavailableException("GitHub is temporarily unavailable. Please try again shortly.", exception);
    }

    private boolean isRateLimitResponse(HttpClientErrorException exception) {
        HttpHeaders headers = exception.getResponseHeaders();
        if (headers != null) {
            String remaining = headers.getFirst("X-RateLimit-Remaining");
            if ("0".equals(remaining)) {
                return true;
            }
            if (headers.getFirst("Retry-After") != null) {
                return true;
            }
        }
        String body = exception.getResponseBodyAsString();
        if (body != null) {
            String lower = body.toLowerCase();
            if (lower.contains("rate limit") || lower.contains("secondary rate limit") || lower.contains("api rate limit exceeded")) {
                return true;
            }
        }
        return false;
    }

    private Long extractRetryAfter(HttpHeaders headers) {
        if (headers == null) return null;
        String retryAfter = headers.getFirst("Retry-After");
        if (retryAfter != null) {
            try {
                return Long.parseLong(retryAfter.trim());
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private Long extractRateLimitReset(HttpHeaders headers) {
        if (headers == null) return null;
        String reset = headers.getFirst("X-RateLimit-Reset");
        if (reset != null) {
            try {
                return Long.parseLong(reset.trim());
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}