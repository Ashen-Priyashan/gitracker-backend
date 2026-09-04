package com.gitracker.controller;

import com.gitracker.config.SecurityConfig;
import com.gitracker.config.SessionAuthenticationFilter;
import com.gitracker.service.AuthService;
import com.gitracker.service.GitHubUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {UserController.class, AuthController.class, HealthController.class})
@Import({SecurityConfig.class, SessionAuthenticationFilter.class})
class SecurityAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitHubUserService gitHubUserService;

    @MockitoBean
    private AuthService authService;

    @Test
    void shouldRejectUnauthenticatedRequestToProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/users/octocat"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectUnauthenticatedRequestToLogout() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowAuthenticatedRequestToProtectedEndpoint() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_ID_KEY, 123L);

        mockMvc.perform(get("/api/users/octocat").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowUnauthenticatedRequestToPublicAuthMe() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowUnauthenticatedRequestToHealth() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }
}
