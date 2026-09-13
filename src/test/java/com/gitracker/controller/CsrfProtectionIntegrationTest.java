package com.gitracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitracker.config.SecurityConfig;
import com.gitracker.config.SessionAuthenticationFilter;
import com.gitracker.dto.LoginRequest;
import com.gitracker.dto.SignUpRequest;
import com.gitracker.dto.UserAuthResponse;
import com.gitracker.service.AuthService;
import com.gitracker.service.GitHubAuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class, GitHubAuthController.class})
@Import({SecurityConfig.class, SessionAuthenticationFilter.class})
class CsrfProtectionIntegrationTest {

    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    private static final String VALID_CSRF_TOKEN = "valid-test-csrf-token-12345";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private GitHubAuthService gitHubAuthService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==========================================
    // 1. Logout CSRF Tests
    // ==========================================

    @Test
    void shouldRejectLogoutWithoutCsrfToken() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_ID_KEY, 123L);

        mockMvc.perform(post("/api/auth/logout")
                        .session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowLogoutWithValidCsrfToken() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_ID_KEY, 123L);

        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .cookie(new Cookie(CSRF_COOKIE_NAME, VALID_CSRF_TOKEN))
                        .header(CSRF_HEADER_NAME, VALID_CSRF_TOKEN))
                .andExpect(status().isOk());
    }

    // ==========================================
    // 2. Login CSRF Tests (Clean Anonymous Session)
    // ==========================================

    @Test
    void shouldRejectLoginWithoutCsrfToken() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "password");

        mockMvc.perform(post("/api/auth/login")
                        .session(new MockHttpSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(authService, never()).login(any(), any());
    }

    @Test
    void shouldAllowLoginWithValidCsrfToken() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "password");
        UserAuthResponse response = new UserAuthResponse(1L, "User", "user@example.com", false, null, null, null);
        when(authService.login(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .session(new MockHttpSession())
                        .cookie(new Cookie(CSRF_COOKIE_NAME, VALID_CSRF_TOKEN))
                        .header(CSRF_HEADER_NAME, VALID_CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    // ==========================================
    // 3. Signup CSRF Tests (Clean Anonymous Session)
    // ==========================================

    @Test
    void shouldRejectSignupWithoutCsrfToken() throws Exception {
        SignUpRequest request = new SignUpRequest("User", "user@example.com", "password123", "password123");

        mockMvc.perform(post("/api/auth/signup")
                        .session(new MockHttpSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(authService, never()).signUp(any(), any());
    }

    @Test
    void shouldAllowSignupWithValidCsrfToken() throws Exception {
        SignUpRequest request = new SignUpRequest("User", "user@example.com", "password123", "password123");
        UserAuthResponse response = new UserAuthResponse(1L, "User", "user@example.com", false, null, null, null);
        when(authService.signUp(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/signup")
                        .session(new MockHttpSession())
                        .cookie(new Cookie(CSRF_COOKIE_NAME, VALID_CSRF_TOKEN))
                        .header(CSRF_HEADER_NAME, VALID_CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    // ==========================================
    // 4. Link GitHub CSRF Tests (Authenticated Session)
    // ==========================================

    @Test
    void shouldRejectLinkGithubWithoutCsrfToken() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_ID_KEY, 123L);

        mockMvc.perform(post("/api/auth/link-github")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "octocat"))))
                .andExpect(status().isForbidden());

        verify(authService, never()).linkGithubUsername(any(), any());
    }

    @Test
    void shouldRejectLinkGithubWithInvalidCsrfToken() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_ID_KEY, 123L);

        mockMvc.perform(post("/api/auth/link-github")
                        .session(session)
                        .cookie(new Cookie(CSRF_COOKIE_NAME, VALID_CSRF_TOKEN))
                        .header(CSRF_HEADER_NAME, "completely-invalid-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "octocat"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectLinkGithubWithMismatchedCookieAndHeader() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_ID_KEY, 123L);

        mockMvc.perform(post("/api/auth/link-github")
                        .session(session)
                        .cookie(new Cookie(CSRF_COOKIE_NAME, "token-aaa"))
                        .header(CSRF_HEADER_NAME, "token-bbb")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "octocat"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowLinkGithubWithValidCsrfTokenAndSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_ID_KEY, 123L);
        UserAuthResponse response = new UserAuthResponse(123L, "User", "user@example.com", true, "octocat", null, null);
        when(authService.linkGithubUsername(eq("octocat"), any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/link-github")
                        .session(session)
                        .cookie(new Cookie(CSRF_COOKIE_NAME, VALID_CSRF_TOKEN))
                        .header(CSRF_HEADER_NAME, VALID_CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "octocat"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.githubUsername").value("octocat"));
    }

    // ==========================================
    // 5. GitHub Disconnect CSRF Tests
    // ==========================================

    @Test
    void shouldRejectDisconnectWithoutCsrfToken() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_ID_KEY, 123L);

        mockMvc.perform(post("/api/github/disconnect")
                        .session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowDisconnectWithValidCsrfToken() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_ID_KEY, 123L);

        mockMvc.perform(post("/api/github/disconnect")
                        .session(session)
                        .cookie(new Cookie(CSRF_COOKIE_NAME, VALID_CSRF_TOKEN))
                        .header(CSRF_HEADER_NAME, VALID_CSRF_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("GitHub connection removed successfully"));
    }

    // ==========================================
    // 6. Token Lifecycle & Dedicated Endpoint Tests
    // ==========================================

    @Test
    void shouldExposeCsrfTokenOnGetCsrfEndpoint() throws Exception {
        mockMvc.perform(get("/api/auth/csrf").session(new MockHttpSession()))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(CSRF_COOKIE_NAME))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value(CSRF_HEADER_NAME))
                .andExpect(jsonPath("$.parameterName").value("_csrf"));
    }

    @Test
    void shouldSetXsrfTokenCookieOnGetRequest() throws Exception {
        mockMvc.perform(get("/api/auth/me").session(new MockHttpSession()))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(CSRF_COOKIE_NAME));
    }

    @Test
    void shouldAllowTokenReuseAcrossMultipleMutatingRequests() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_ID_KEY, 123L);

        when(authService.linkGithubUsername(eq("octocat"), any()))
                .thenReturn(new UserAuthResponse(123L, "User", "u@ex.com", true, "octocat", null, null));

        // First mutating request with double-submit cookie & header
        mockMvc.perform(post("/api/auth/link-github")
                        .session(session)
                        .cookie(new Cookie(CSRF_COOKIE_NAME, VALID_CSRF_TOKEN))
                        .header(CSRF_HEADER_NAME, VALID_CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "octocat"))))
                .andExpect(status().isOk());

        // Second mutating request using same session and same CSRF token
        mockMvc.perform(post("/api/github/disconnect")
                        .session(session)
                        .cookie(new Cookie(CSRF_COOKIE_NAME, VALID_CSRF_TOKEN))
                        .header(CSRF_HEADER_NAME, VALID_CSRF_TOKEN))
                .andExpect(status().isOk());
    }

    // ==========================================
    // 7. Security Attack Simulation (Vulnerability Regression)
    // ==========================================

    @Test
    void shouldBlockCrossSiteLinkGithubAttackWithoutCsrfToken() throws Exception {
        // Given: An authenticated victim session exists
        MockHttpSession victimSession = new MockHttpSession();
        victimSession.setAttribute(AuthService.SESSION_USER_ID_KEY, 999L);

        // When: An attacker attempts a cross-origin forgery to link their GitHub username
        // using the victim's ambient session credentials without a valid CSRF token
        mockMvc.perform(post("/api/auth/link-github")
                        .session(victimSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"attacker_github_handle\"}"))
                .andExpect(status().isForbidden());

        // Then: The attack is blocked by Spring Security and backend logic is never invoked
        verify(authService, never()).linkGithubUsername(any(), any());
    }
}
