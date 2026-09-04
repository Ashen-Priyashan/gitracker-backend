package com.gitracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitracker.config.SecurityConfig;
import com.gitracker.dto.LoginRequest;
import com.gitracker.dto.UserAuthResponse;
import com.gitracker.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class CsrfProtectionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRejectStateChangingRequestWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowStateChangingRequestWithValidCsrfToken() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_ID_KEY, 123L);

        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowLoginWithoutCsrfTokenForInitialAuthentication() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "password");
        UserAuthResponse response = new UserAuthResponse(1L, "User", "user@example.com", false, null, null, null);
        when(authService.login(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldSetXsrfTokenCookieOnGetRequest() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }
}
