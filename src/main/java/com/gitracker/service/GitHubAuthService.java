package com.gitracker.service;

import com.gitracker.client.GitHubApiClient;
import com.gitracker.dto.GitHubConnectionStatusResponse;
import com.gitracker.dto.GitHubRepositoryDto;
import com.gitracker.dto.GitHubUserDto;
import com.gitracker.dto.RepositoryResponse;
import com.gitracker.entity.GitHubAppConnection;
import com.gitracker.repository.GitHubAppConnectionRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class GitHubAuthService {

    private static final String STATE_SESSION_KEY = "GITHUB_OAUTH_STATE";
    private static final String CONNECTED_USER_KEY = "CONNECTED_GITHUB_USERNAME";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String frontendUrl;
    private final GitHubApiClient gitHubApiClient;
    private final GitHubAppConnectionRepository connectionRepository;
    private final RestClient oauthRestClient;

    public GitHubAuthService(
            @Value("${github.app.client-id:}") String clientId,
            @Value("${github.app.client-secret:}") String clientSecret,
            @Value("${github.app.redirect-uri:http://localhost:8080/api/github/callback}") String redirectUri,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
            GitHubApiClient gitHubApiClient,
            GitHubAppConnectionRepository connectionRepository
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.frontendUrl = frontendUrl;
        this.gitHubApiClient = gitHubApiClient;
        this.connectionRepository = connectionRepository;
        this.oauthRestClient = RestClient.builder()
                .baseUrl("https://github.com")
                .build();
    }

    public String generateAuthorizationUrl(HttpSession session) {
        String state = UUID.randomUUID().toString();
        session.setAttribute(STATE_SESSION_KEY, state);

        return "https://github.com/login/oauth/authorize" +
                "?client_id=" + clientId +
                "&redirect_uri=" + redirectUri +
                "&state=" + state;
    }

    @SuppressWarnings("unchecked")
    public String handleCallback(String code, String state, HttpSession session) {
        String sessionState = (String) session.getAttribute(STATE_SESSION_KEY);

        if (sessionState == null || !sessionState.equals(state)) {
            return frontendUrl + "/settings?error=invalid_state";
        }
        session.removeAttribute(STATE_SESSION_KEY);

        if (code == null || code.isBlank()) {
            return frontendUrl + "/settings?error=missing_code";
        }

        try {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("code", code);
            body.add("redirect_uri", redirectUri);

            Map<String, Object> response = oauthRestClient.post()
                    .uri("/login/oauth/access_token")
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response == null || !response.containsKey("access_token")) {
                return frontendUrl + "/settings?error=token_exchange_failed";
            }

            String accessToken = (String) response.get("access_token");
            String tokenType = (String) response.get("token_type");
            String scope = (String) response.get("scope");

            GitHubUserDto gitHubUser = gitHubApiClient.getAuthenticatedUser(accessToken);

            Optional<GitHubAppConnection> existingOpt = connectionRepository.findByGithubId(gitHubUser.id());
            GitHubAppConnection connection = existingOpt.orElseGet(() -> GitHubAppConnection.builder()
                    .githubId(gitHubUser.id())
                    .build());

            connection.setUsername(gitHubUser.login());
            connection.setName(gitHubUser.name());
            connection.setAvatarUrl(gitHubUser.avatarUrl());
            connection.setAccessToken(accessToken);
            connection.setTokenType(tokenType);
            connection.setScope(scope);

            connectionRepository.save(connection);

            session.setAttribute(CONNECTED_USER_KEY, gitHubUser.login());

            return frontendUrl + "/settings?connected=true";

        } catch (Exception e) {
            return frontendUrl + "/settings?error=auth_failed";
        }
    }

    public GitHubConnectionStatusResponse getConnectionStatus(HttpSession session) {
        String username = (String) session.getAttribute(CONNECTED_USER_KEY);

        if (username == null || username.isBlank()) {
            return new GitHubConnectionStatusResponse(false, null, null, null);
        }

        Optional<GitHubAppConnection> connectionOpt = connectionRepository.findByUsername(username);
        if (connectionOpt.isEmpty()) {
            session.removeAttribute(CONNECTED_USER_KEY);
            return new GitHubConnectionStatusResponse(false, null, null, null);
        }

        GitHubAppConnection connection = connectionOpt.get();
        return new GitHubConnectionStatusResponse(
                true,
                connection.getUsername(),
                connection.getAvatarUrl(),
                connection.getConnectedAt() != null ? connection.getConnectedAt().toString() : null
        );
    }

    public void disconnect(HttpSession session) {
        String username = (String) session.getAttribute(CONNECTED_USER_KEY);
        if (username != null) {
            connectionRepository.findByUsername(username)
                    .ifPresent(connectionRepository::delete);
            session.removeAttribute(CONNECTED_USER_KEY);
        }
    }

    public List<RepositoryResponse> getAuthenticatedRepositories(HttpSession session) {
        String username = (String) session.getAttribute(CONNECTED_USER_KEY);

        if (username == null) {
            throw new IllegalStateException("No authenticated GitHub session found.");
        }

        GitHubAppConnection connection = connectionRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("GitHub connection details not found."));

        GitHubRepositoryDto[] repos = gitHubApiClient.getAuthenticatedUserRepositories(connection.getAccessToken());

        return Arrays.stream(repos)
                .map(repo -> new RepositoryResponse(
                        repo.name(),
                        repo.description(),
                        repo.stars(),
                        repo.forks(),
                        repo.watchers(),
                        repo.language(),
                        repo.defaultBranch() != null ? repo.defaultBranch() : "main",
                        repo.openIssues(),
                        repo.license() != null ? repo.license().name() : null,
                        repo.topics() != null ? repo.topics() : List.of(),
                        repo.htmlUrl(),
                        repo.size(),
                        repo.archived(),
                        repo.createdAt(),
                        repo.updatedAt()
                ))
                .toList();
    }
}
