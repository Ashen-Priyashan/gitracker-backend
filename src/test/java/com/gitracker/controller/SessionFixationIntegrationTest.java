package com.gitracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitracker.config.CsrfCookieFilter;
import com.gitracker.config.SecurityConfig;
import com.gitracker.config.SessionAuthenticationFilter;
import com.gitracker.dto.LoginRequest;
import com.gitracker.dto.SignUpRequest;
import com.gitracker.dto.UserAuthResponse;
import com.gitracker.entity.UserEntity;
import com.gitracker.repository.UserRepository;
import com.gitracker.service.AuthService;
import com.gitracker.service.GitHubUserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, UserController.class})
@Import({SecurityConfig.class, SessionAuthenticationFilter.class, AuthService.class})
class SessionFixationIntegrationTest {

    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    private static final String VALID_CSRF_TOKEN = "session-fixation-csrf-token-12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private GitHubUserService gitHubUserService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserEntity existingUser;

    @BeforeEach
    void setUp() {
        existingUser = UserEntity.builder()
                .id(1L)
                .name("Alice")
                .email("alice@example.com")
                .password("$2a$10$hashedsecret")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // =========================================================================
    // 1. Session ID Rotation on Login
    // =========================================================================

    @Test
    void shouldRotateSessionIdOnLogin() throws Exception {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("password123", "$2a$10$hashedsecret")).thenReturn(true);

        MockHttpSession anonymousSession = new MockHttpSession();
        String preLoginSessionId = anonymousSession.getId();

        LoginRequest loginRequest = new LoginRequest("alice@example.com", "password123");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .session(anonymousSession)
                        .cookie(new Cookie(CSRF_COOKIE_NAME, VALID_CSRF_TOKEN))
                        .header(CSRF_HEADER_NAME, VALID_CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andReturn();

        MockHttpSession postLoginSession = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(postLoginSession);
        assertNotEquals(preLoginSessionId, postLoginSession.getId(),
                "Session ID must be rotated upon successful login");
        assertEquals(1L, postLoginSession.getAttribute(AuthService.SESSION_USER_ID_KEY),
                "Authenticated user ID must be stored in the rotated session");
    }

    // =========================================================================
    // 2. Session ID Rotation on Signup
    // =========================================================================

    @Test
    void shouldRotateSessionIdOnSignup() throws Exception {
        UserEntity newUser = UserEntity.builder()
                .id(2L)
                .name("Bob")
                .email("bob@example.com")
                .password("$2a$10$hashedbobpassword")
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashedbobpassword");
        when(userRepository.save(any(UserEntity.class))).thenReturn(newUser);

        MockHttpSession anonymousSession = new MockHttpSession();
        String preSignupSessionId = anonymousSession.getId();

        SignUpRequest signUpRequest = new SignUpRequest("Bob", "bob@example.com", "password123", "password123");

        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .session(anonymousSession)
                        .cookie(new Cookie(CSRF_COOKIE_NAME, VALID_CSRF_TOKEN))
                        .header(CSRF_HEADER_NAME, VALID_CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signUpRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.email").value("bob@example.com"))
                .andReturn();

        MockHttpSession postSignupSession = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(postSignupSession);
        assertNotEquals(preSignupSessionId, postSignupSession.getId(),
                "Session ID must be rotated upon successful signup");
        assertEquals(2L, postSignupSession.getAttribute(AuthService.SESSION_USER_ID_KEY),
                "Authenticated user ID must be stored in the rotated session");
    }

    // =========================================================================
    // 3. Attack Simulation: Pre-Authentication Session Cannot Access Authenticated State
    // =========================================================================

    @Test
    void shouldEnsurePreAuthenticationSessionCannotAccessAuthenticatedResources() throws Exception {
        // Scenario: Attacker establishes/knows pre-auth session A
        MockHttpSession preAuthSession = new MockHttpSession();
        String attackerFixedSessionId = preAuthSession.getId();

        // Pre-auth session contains no authentication initially
        assertNull(preAuthSession.getAttribute(AuthService.SESSION_USER_ID_KEY));

        // Victim authenticates using this session context
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("password123", "$2a$10$hashedsecret")).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));

        LoginRequest loginRequest = new LoginRequest("alice@example.com", "password123");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .session(preAuthSession)
                        .cookie(new Cookie(CSRF_COOKIE_NAME, VALID_CSRF_TOKEN))
                        .header(CSRF_HEADER_NAME, VALID_CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession authenticatedVictimSession = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(authenticatedVictimSession);
        String newSessionId = authenticatedVictimSession.getId();

        // 1. The session identifier MUST have changed from the pre-authentication ID
        assertNotEquals(attackerFixedSessionId, newSessionId,
                "Authenticated session ID must differ from attacker-known pre-auth session ID");

        // 2. An attacker presenting a session with the pre-authentication ID must be unauthenticated
        MockHttpSession attackerSession = new MockHttpSession(null, attackerFixedSessionId);
        mockMvc.perform(get("/api/auth/me")
                        .session(attackerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));

        // 3. An attacker presenting the pre-authentication session ID to a protected endpoint is rejected
        mockMvc.perform(get("/api/users/octocat")
                        .session(attackerSession))
                .andExpect(status().isUnauthorized());

        // 4. The legitimate rotated session successfully accesses authenticated resources
        mockMvc.perform(get("/api/auth/me")
                        .session(authenticatedVictimSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.email").value("alice@example.com"));
    }

    // =========================================================================
    // 4. Authenticated Session Persistence After Rotation
    // =========================================================================

    @Test
    void shouldPreserveAuthenticatedUserAfterSessionRotation() throws Exception {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("password123", "$2a$10$hashedsecret")).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));

        MockHttpSession session = new MockHttpSession();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .cookie(new Cookie(CSRF_COOKIE_NAME, VALID_CSRF_TOKEN))
                        .header(CSRF_HEADER_NAME, VALID_CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("alice@example.com", "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession rotatedSession = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/api/auth/me").session(rotatedSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.user.name").value("Alice"))
                .andExpect(jsonPath("$.user.email").value("alice@example.com"));
    }

    // =========================================================================
    // 5. CSRF + Login Integration Flow
    // =========================================================================

    @Test
    void shouldSucceedLoginWithCsrfAndRotateSession() throws Exception {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("password123", "$2a$10$hashedsecret")).thenReturn(true);

        MockHttpSession session = new MockHttpSession();
        String initialSessionId = session.getId();

        // 1. Proactively obtain CSRF token
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asText();

        // 2. Perform login with obtained CSRF token and cookie
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .cookie(new Cookie(CSRF_COOKIE_NAME, token))
                        .header(CSRF_HEADER_NAME, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("alice@example.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andReturn();

        MockHttpSession postLoginSession = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotEquals(initialSessionId, postLoginSession.getId(),
                "Session ID must rotate when authenticating with proactive CSRF token");
    }

    // =========================================================================
    // 6. CSRF + Signup Integration Flow
    // =========================================================================

    @Test
    void shouldSucceedSignupWithCsrfAndRotateSession() throws Exception {
        UserEntity newUser = UserEntity.builder()
                .id(3L)
                .name("Charlie")
                .email("charlie@example.com")
                .password("$2a$10$hashedcharliepassword")
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.existsByEmail("charlie@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashedcharliepassword");
        when(userRepository.save(any(UserEntity.class))).thenReturn(newUser);

        MockHttpSession session = new MockHttpSession();
        String initialSessionId = session.getId();

        // 1. Proactively obtain CSRF token
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asText();

        // 2. Perform signup with obtained CSRF token and cookie
        SignUpRequest signUpRequest = new SignUpRequest("Charlie", "charlie@example.com", "password123", "password123");

        MvcResult signupResult = mockMvc.perform(post("/api/auth/signup")
                        .session(session)
                        .cookie(new Cookie(CSRF_COOKIE_NAME, token))
                        .header(CSRF_HEADER_NAME, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signUpRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andReturn();

        MockHttpSession postSignupSession = (MockHttpSession) signupResult.getRequest().getSession(false);
        assertNotEquals(initialSessionId, postSignupSession.getId(),
                "Session ID must rotate when registering with proactive CSRF token");
    }

    // =========================================================================
    // 7. Failed Authentication Does NOT Rotate Session
    // =========================================================================

    @Test
    void shouldNotRotateSessionIdWhenLoginFailsWithInvalidCredentials() throws Exception {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("wrongpass", "$2a$10$hashedsecret")).thenReturn(false);

        MockHttpSession session = new MockHttpSession();
        String originalSessionId = session.getId();

        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .cookie(new Cookie(CSRF_COOKIE_NAME, VALID_CSRF_TOKEN))
                        .header(CSRF_HEADER_NAME, VALID_CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("alice@example.com", "wrongpass"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        assertEquals(originalSessionId, session.getId(),
                "Session ID must NOT rotate when login credentials fail");
        assertNull(session.getAttribute(AuthService.SESSION_USER_ID_KEY));
    }

    @Test
    void shouldNotRotateSessionIdWhenSignupFailsWithDuplicateEmail() throws Exception {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        MockHttpSession session = new MockHttpSession();
        String originalSessionId = session.getId();

        SignUpRequest signUpRequest = new SignUpRequest("Alice", "alice@example.com", "password123", "password123");

        mockMvc.perform(post("/api/auth/signup")
                        .session(session)
                        .cookie(new Cookie(CSRF_COOKIE_NAME, VALID_CSRF_TOKEN))
                        .header(CSRF_HEADER_NAME, VALID_CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signUpRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));

        assertEquals(originalSessionId, session.getId(),
                "Session ID must NOT rotate when signup validation fails");
        assertNull(session.getAttribute(AuthService.SESSION_USER_ID_KEY));
    }

    // =========================================================================
    // 8. Pre-Existing Session Attributes (OAuth State) Preserved Across Rotation
    // =========================================================================

    @Test
    void shouldPreservePreExistingSessionAttributesLikeOAuthStateAcrossRotation() throws Exception {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("password123", "$2a$10$hashedsecret")).thenReturn(true);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("GITHUB_OAUTH_STATE", "oauth-state-xyz-123");
        String initialSessionId = session.getId();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .cookie(new Cookie(CSRF_COOKIE_NAME, VALID_CSRF_TOKEN))
                        .header(CSRF_HEADER_NAME, VALID_CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("alice@example.com", "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession rotatedSession = (MockHttpSession) result.getRequest().getSession(false);
        assertNotEquals(initialSessionId, rotatedSession.getId());
        assertEquals("oauth-state-xyz-123", rotatedSession.getAttribute("GITHUB_OAUTH_STATE"),
                "Pre-existing session attributes like OAuth state must be preserved across session ID rotation");
        assertEquals(1L, rotatedSession.getAttribute(AuthService.SESSION_USER_ID_KEY));
    }

    // =========================================================================
    // 9. Session Invalidation on Logout
    // =========================================================================

    @Test
    void shouldInvalidateSessionOnLogout() throws Exception {
        MockHttpSession authenticatedSession = new MockHttpSession();
        authenticatedSession.setAttribute(AuthService.SESSION_USER_ID_KEY, 1L);

        mockMvc.perform(post("/api/auth/logout")
                        .session(authenticatedSession)
                        .cookie(new Cookie(CSRF_COOKIE_NAME, VALID_CSRF_TOKEN))
                        .header(CSRF_HEADER_NAME, VALID_CSRF_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        org.junit.jupiter.api.Assertions.assertTrue(authenticatedSession.isInvalid(),
                "Session must be invalidated on logout");
    }
}
