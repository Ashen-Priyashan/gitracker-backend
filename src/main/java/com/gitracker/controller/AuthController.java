package com.gitracker.controller;

import com.gitracker.dto.LoginRequest;
import com.gitracker.dto.SignUpRequest;
import com.gitracker.dto.UserAuthResponse;
import com.gitracker.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(
        name = "Authentication",
        description = "Operations for Gitracker user sign up, login, logout, and session management"
)
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(
            summary = "Gitracker account sign up",
            description = "Registers a new Gitracker user account and establishes a logged-in session."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or duplicate email")
    })
    @PostMapping("/signup")
    public ResponseEntity<?> signUp(@RequestBody SignUpRequest request, HttpServletRequest httpRequest) {
        try {
            UserAuthResponse response = authService.signUp(request, httpRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @Operation(
            summary = "Gitracker user login",
            description = "Authenticates user credentials and establishes a logged-in session."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "400", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            UserAuthResponse response = authService.login(request, httpRequest);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @Operation(
            summary = "Gitracker user logout",
            description = "Invalidates active session and logs out user."
    )
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpSession session) {
        authService.logout(session);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @Operation(
            summary = "Get current authenticated user session",
            description = "Returns current Gitracker user profile and GitHub connection status if logged in."
    )
    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {
        UserAuthResponse user = authService.getCurrentUser(session);
        if (user == null) {
            return ResponseEntity.ok(Map.of("authenticated", false));
        }
        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "user", user
        ));
    }

    @Operation(
            summary = "Get CSRF token",
            description = "Resolves the active CSRF token and sets the XSRF-TOKEN cookie for the SPA client."
    )
    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> getCsrfToken(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            csrfToken = (CsrfToken) request.getAttribute("_csrf");
        }
        String token = csrfToken != null ? csrfToken.getToken() : "";
        String headerName = csrfToken != null ? csrfToken.getHeaderName() : "X-XSRF-TOKEN";
        String parameterName = csrfToken != null ? csrfToken.getParameterName() : "_csrf";
        return ResponseEntity.ok(Map.of(
                "token", token,
                "headerName", headerName,
                "parameterName", parameterName
        ));
    }

    @Operation(
            summary = "Link GitHub username to account",
            description = "Links a GitHub username directly to the authenticated Gitracker user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "GitHub username linked successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid username or unauthenticated")
    })
    @PostMapping("/link-github")
    public ResponseEntity<?> linkGithub(@RequestBody Map<String, String> request, HttpSession session) {
        try {
            String username = request.get("username");
            UserAuthResponse response = authService.linkGithubUsername(username, session);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
