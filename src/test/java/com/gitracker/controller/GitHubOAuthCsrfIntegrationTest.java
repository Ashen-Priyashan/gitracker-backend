package com.gitracker.controller;

import com.gitracker.config.SecurityConfig;
import com.gitracker.config.SessionAuthenticationFilter;
import com.gitracker.service.GitHubAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GitHubAuthController.class)
@Import({SecurityConfig.class, SessionAuthenticationFilter.class})
class GitHubOAuthCsrfIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitHubAuthService gitHubAuthService;

    @Test
    void shouldAllowOAuthCallbackWithoutSpringCsrfTokenWhenStateIsValid() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("GITHUB_OAUTH_STATE", "valid-oauth-state-123");

        when(gitHubAuthService.handleCallback(eq("auth-code-xyz"), eq("valid-oauth-state-123"), any()))
                .thenReturn("http://localhost:5173/settings?connected=true");

        // GET request without any CSRF token header or cookie must be allowed by Spring Security
        // and processed through OAuth state validation
        mockMvc.perform(get("/api/github/callback")
                        .session(session)
                        .param("code", "auth-code-xyz")
                        .param("state", "valid-oauth-state-123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:5173/settings?connected=true"));

        verify(gitHubAuthService).handleCallback(eq("auth-code-xyz"), eq("valid-oauth-state-123"), any());
    }

    @Test
    void shouldRejectOAuthCallbackWhenStateIsMissing() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("GITHUB_OAUTH_STATE", "valid-oauth-state-123");

        when(gitHubAuthService.handleCallback(eq("auth-code-xyz"), isNull(), any()))
                .thenReturn("http://localhost:5173/settings?error=invalid_state");

        mockMvc.perform(get("/api/github/callback")
                        .session(session)
                        .param("code", "auth-code-xyz"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:5173/settings?error=invalid_state"));

        verify(gitHubAuthService).handleCallback(eq("auth-code-xyz"), isNull(), any());
    }

    @Test
    void shouldRejectOAuthCallbackWhenStateIsMismatched() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("GITHUB_OAUTH_STATE", "legitimate-session-state");

        when(gitHubAuthService.handleCallback(eq("auth-code-xyz"), eq("attacker-injected-state"), any()))
                .thenReturn("http://localhost:5173/settings?error=invalid_state");

        mockMvc.perform(get("/api/github/callback")
                        .session(session)
                        .param("code", "auth-code-xyz")
                        .param("state", "attacker-injected-state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:5173/settings?error=invalid_state"));

        verify(gitHubAuthService).handleCallback(eq("auth-code-xyz"), eq("attacker-injected-state"), any());
    }
}
