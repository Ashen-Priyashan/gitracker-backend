package com.gitracker.controller;

import com.gitracker.dto.GitHubConnectionStatusResponse;
import com.gitracker.service.GitHubAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GitHubAuthController.class)
class GitHubAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitHubAuthService gitHubAuthService;

    @Test
    void shouldReturnAuthorizeUrl() throws Exception {
        when(gitHubAuthService.generateAuthorizationUrl(any()))
                .thenReturn("https://github.com/login/oauth/authorize?client_id=test&state=123");

        mockMvc.perform(get("/api/github/authorize")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://github.com/login/oauth/authorize?client_id=test&state=123"));
    }

    @Test
    void shouldReturnConnectionStatusWhenNotConnected() throws Exception {
        GitHubConnectionStatusResponse statusResponse = new GitHubConnectionStatusResponse(false, null, null, null);
        when(gitHubAuthService.getConnectionStatus(any())).thenReturn(statusResponse);

        mockMvc.perform(get("/api/github/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false));
    }

    @Test
    void shouldReturnConnectionStatusWhenConnected() throws Exception {
        GitHubConnectionStatusResponse statusResponse = new GitHubConnectionStatusResponse(
                true, "octocat", "https://avatar.url", "2026-08-17T18:00:00"
        );
        when(gitHubAuthService.getConnectionStatus(any())).thenReturn(statusResponse);

        mockMvc.perform(get("/api/github/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.username").value("octocat"));
    }

    @Test
    void shouldDisconnectSuccessfully() throws Exception {
        MockHttpSession session = new MockHttpSession();
        doNothing().when(gitHubAuthService).disconnect(any());

        mockMvc.perform(post("/api/github/disconnect").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("GitHub connection removed successfully"));
    }
}
