package com.gitracker.controller;

import com.gitracker.dto.GitHubConnectionStatusResponse;
import com.gitracker.dto.RepositoryResponse;
import com.gitracker.service.GitHubAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Tag(
        name = "GitHub Authorization",
        description = "Operations for connecting and managing GitHub App user authorization"
)
@RestController
@RequestMapping("/api/github")
public class GitHubAuthController {

    private final GitHubAuthService gitHubAuthService;

    public GitHubAuthController(GitHubAuthService gitHubAuthService) {
        this.gitHubAuthService = gitHubAuthService;
    }

    @Operation(
            summary = "Start GitHub authorization",
            description = "Generates the GitHub authorization URL for connecting a GitHub account."
    )
    @GetMapping("/authorize")
    public ResponseEntity<?> authorize(HttpSession session) {
        try {
            String authUrl = gitHubAuthService.generateAuthorizationUrl(session);
            return ResponseEntity.ok(Map.of("url", authUrl));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @Operation(
            summary = "GitHub OAuth callback",
            description = "Handles callback from GitHub authorization, exchanges code for access token, and sets connection state."
    )
    @GetMapping("/callback")
    public void callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            HttpSession session,
            HttpServletResponse response
    ) throws IOException {
        String redirectUrl = gitHubAuthService.handleCallback(code, state, session);
        response.sendRedirect(redirectUrl);
    }

    @Operation(
            summary = "Check GitHub connection status",
            description = "Returns whether a GitHub account is currently connected to the active session."
    )
    @GetMapping("/status")
    public ResponseEntity<GitHubConnectionStatusResponse> getStatus(HttpSession session) {
        return ResponseEntity.ok(gitHubAuthService.getConnectionStatus(session));
    }

    @Operation(
            summary = "Disconnect GitHub connection",
            description = "Disconnects the current GitHub account connection and clears session."
    )
    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, String>> disconnect(HttpSession session) {
        gitHubAuthService.disconnect(session);
        return ResponseEntity.ok(Map.of("message", "GitHub connection removed successfully"));
    }

    @Operation(
            summary = "Get authenticated user repositories",
            description = "Retrieves repositories for the currently connected GitHub user, including private repositories."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Repositories retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "User is not connected to GitHub")
    })
    @GetMapping("/user/repositories")
    public ResponseEntity<List<RepositoryResponse>> getAuthenticatedRepositories(HttpSession session) {
        try {
            List<RepositoryResponse> repos = gitHubAuthService.getAuthenticatedRepositories(session);
            return ResponseEntity.ok(repos);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
