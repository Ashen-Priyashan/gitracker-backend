package com.gitracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitracker.dto.LoginRequest;
import com.gitracker.entity.UserEntity;
import com.gitracker.exception.RateLimitExceededException;
import com.gitracker.repository.UserRepository;
import com.gitracker.service.AuthService;
import com.gitracker.service.LoginRateLimiter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LoginRateLimitIntegrationTest {

    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserEntity validUser;
    private String csrfToken;

    @BeforeEach
    void setUp() throws Exception {
        cleanRedisLoginKeys();

        validUser = UserEntity.builder()
                .id(100L)
                .name("Rate Limit Test User")
                .email("ratelimit.user@example.com")
                .password("$2a$10$hashedsecretpassword123")
                .createdAt(LocalDateTime.now())
                .build();

        // Default mock behaviors
        when(userRepository.findByEmail("ratelimit.user@example.com")).thenReturn(Optional.of(validUser));
        when(passwordEncoder.matches("ValidPassword123!", "$2a$10$hashedsecretpassword123")).thenReturn(true);
        when(passwordEncoder.matches(argThat(pwd -> !"ValidPassword123!".equals(pwd)), any())).thenReturn(false);

        // Fetch fresh CSRF token
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asText();
    }

    @AfterEach
    void tearDown() {
        cleanRedisLoginKeys();
    }

    private void cleanRedisLoginKeys() {
        try {
            Set<String> accountKeys = redisTemplate.keys("gitracker:auth:login:account:*");
            if (accountKeys != null && !accountKeys.isEmpty()) {
                redisTemplate.delete(accountKeys);
            }
            Set<String> ipKeys = redisTemplate.keys("gitracker:auth:login:ip:*");
            if (ipKeys != null && !ipKeys.isEmpty()) {
                redisTemplate.delete(ipKeys);
            }
        } catch (Exception e) {
            // Ignore in cleanup
        }
    }

    // =========================================================================
    // 1. Valid Login Within Rate Limits
    // =========================================================================

    @Test
    void shouldAllowValidLoginWithinRateLimit() throws Exception {
        LoginRequest request = new LoginRequest("ratelimit.user@example.com", "ValidPassword123!");

        mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr("10.0.0.1"); return req; })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(validUser.getId()))
                .andExpect(jsonPath("$.email").value("ratelimit.user@example.com"))
                .andExpect(jsonPath("$.name").value("Rate Limit Test User"));
    }

    // =========================================================================
    // 2. Generic Failure on Wrong Password
    // =========================================================================

    @Test
    void shouldReturnGenericAuthenticationFailureForInvalidPassword() throws Exception {
        LoginRequest request = new LoginRequest("ratelimit.user@example.com", "WrongPassword!");

        mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr("10.0.0.2"); return req; })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    // =========================================================================
    // 3. Generic Failure on Non-Existent Account
    // =========================================================================

    @Test
    void shouldReturnGenericAuthenticationFailureForNonexistentAccount() throws Exception {
        when(userRepository.findByEmail("nonexistent.user@example.com")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("nonexistent.user@example.com", "SomePassword!");

        mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr("10.0.0.3"); return req; })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    // =========================================================================
    // 4. Equivalent Externally Observable Authentication Behavior
    // =========================================================================

    @Test
    void shouldReturnEquivalentObservableBehaviorForNonexistentAccount() throws Exception {
        when(userRepository.findByEmail("ghost.user@example.com")).thenReturn(Optional.empty());

        // Attempt failure for existing user
        LoginRequest existingUserRequest = new LoginRequest("ratelimit.user@example.com", "WrongPassword123!");
        MvcResult existingResult = mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr("10.0.0.4"); return req; })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(existingUserRequest)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // Attempt failure for non-existent user
        LoginRequest nonexistentUserRequest = new LoginRequest("ghost.user@example.com", "WrongPassword123!");
        MvcResult nonexistentResult = mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr("10.0.0.5"); return req; })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nonexistentUserRequest)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // 1. Identical HTTP Status Code
        assertEquals(existingResult.getResponse().getStatus(), nonexistentResult.getResponse().getStatus());

        // 2. Identical Generic Error Message & Body Structure
        String existingBody = existingResult.getResponse().getContentAsString();
        String nonexistentBody = nonexistentResult.getResponse().getContentAsString();
        assertEquals(objectMapper.readTree(existingBody).get("message").asText(),
                objectMapper.readTree(nonexistentBody).get("message").asText());
        assertEquals("Invalid email or password", objectMapper.readTree(existingBody).get("message").asText());

        // 3. No account-existence-specific headers
        assertNull(existingResult.getResponse().getHeader("X-Account-Exists"));
        assertNull(nonexistentResult.getResponse().getHeader("X-Account-Exists"));

        // 4. Identical Throttling Semantics (both increment Redis failure keys)
        String existingKey = loginRateLimiter.buildAccountKey("ratelimit.user@example.com");
        String nonexistentKey = loginRateLimiter.buildAccountKey("ghost.user@example.com");
        assertEquals("1", redisTemplate.opsForValue().get(existingKey));
        assertEquals("1", redisTemplate.opsForValue().get(nonexistentKey));
    }

    // =========================================================================
    // 5. Account Throttling Threshold Semantics (Attempts 1-5 permitted, 6th throttled)
    // =========================================================================

    @Test
    void shouldThrottleRepeatedFailedLoginAttempts() throws Exception {
        String clientIp = "10.0.0.6";
        LoginRequest badRequest = new LoginRequest("ratelimit.user@example.com", "WrongPass!");

        // Failures 1 through 5: permitted, returns 400 Bad Request
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(req -> { req.setRemoteAddr(clientIp); return req; })
                            .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                            .header(CSRF_HEADER_NAME, csrfToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invalid email or password"));
        }

        // Attempt 6: Pre-check sees count >= 5 -> Throttled with 429 Too Many Requests
        MvcResult throttledResult = mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr(clientIp); return req; })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber())
                .andExpect(jsonPath("$.resetTimeSeconds").isNumber())
                .andReturn();

        String retryAfterHeader = throttledResult.getResponse().getHeader("Retry-After");
        assertNotNull(retryAfterHeader);
        long retryAfter = Long.parseLong(retryAfterHeader);
        assertTrue(retryAfter > 0 && retryAfter <= 300, "Retry-After should be between 1 and 300 seconds");
    }

    // =========================================================================
    // 6. Monotonic Non-Refreshing TTL (Fixed Window, Prevents Indefinite Lockout)
    // =========================================================================

    @Test
    void shouldProveTtlIsNotRefreshedOnSubsequentFailures() throws Exception {
        String clientIp = "10.0.0.7";
        String email = "ttl.test@example.com";
        String accountKey = loginRateLimiter.buildAccountKey(email);
        LoginRequest badRequest = new LoginRequest(email, "WrongPass!");

        // Failure 1
        mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr(clientIp); return req; })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest());

        Long ttl1 = redisTemplate.getExpire(accountKey, TimeUnit.SECONDS);
        assertNotNull(ttl1);
        assertTrue(ttl1 > 0 && ttl1 <= 300);

        // Sleep 1.2 seconds to allow TTL to decrease
        Thread.sleep(1200);

        // Failure 2
        mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr(clientIp); return req; })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest());

        Long ttl2 = redisTemplate.getExpire(accountKey, TimeUnit.SECONDS);
        assertNotNull(ttl2);

        // TTL must NOT be reset back to 300s; it must decrease monotonically
        assertTrue(ttl2 <= ttl1, "TTL must not be extended on subsequent failures (ttl1=" + ttl1 + ", ttl2=" + ttl2 + ")");
    }

    // =========================================================================
    // 7. Successful Login Clears Account Failure State
    // =========================================================================

    @Test
    void shouldClearAccountFailuresAfterSuccessfulLogin() throws Exception {
        String clientIp = "10.0.0.8";
        String accountKey = loginRateLimiter.buildAccountKey("ratelimit.user@example.com");

        // 3 failed logins
        LoginRequest badRequest = new LoginRequest("ratelimit.user@example.com", "WrongPass!");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(req -> { req.setRemoteAddr(clientIp); return req; })
                            .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                            .header(CSRF_HEADER_NAME, csrfToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isBadRequest());
        }

        assertEquals("3", redisTemplate.opsForValue().get(accountKey));

        // 1 successful login
        LoginRequest validRequest = new LoginRequest("ratelimit.user@example.com", "ValidPassword123!");
        mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr(clientIp); return req; })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk());

        // Account failure key must be deleted
        assertNull(redisTemplate.opsForValue().get(accountKey), "Account failure key must be removed after successful authentication");
    }

    // =========================================================================
    // 8. IP Abuse State Preserved When One Account Logs In
    // =========================================================================

    @Test
    void shouldNotClearIpAbuseStateWhenOneAccountLogsInSuccessfully() throws Exception {
        String clientIp = "10.0.0.9";
        String ipKey = loginRateLimiter.buildIpKey(clientIp);

        // 3 failed attempts on this IP
        LoginRequest badRequest = new LoginRequest("ratelimit.user@example.com", "WrongPass!");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(req -> { req.setRemoteAddr(clientIp); return req; })
                            .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                            .header(CSRF_HEADER_NAME, csrfToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isBadRequest());
        }

        assertEquals("3", redisTemplate.opsForValue().get(ipKey));

        // Successful login
        LoginRequest validRequest = new LoginRequest("ratelimit.user@example.com", "ValidPassword123!");
        mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr(clientIp); return req; })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk());

        // IP key must still exist with count 3
        assertEquals("3", redisTemplate.opsForValue().get(ipKey), "IP failure counter must be preserved across successful account login");
    }

    // =========================================================================
    // 9. IP Throttling Threshold Semantics (Failures 1-20 permitted, 21st throttled)
    // =========================================================================

    @Test
    void shouldThrottleRepeatedAttemptsFromSameClient() throws Exception {
        String clientIp = "10.0.0.10";

        // 20 failed attempts across 20 distinct accounts from same IP
        for (int i = 1; i <= 20; i++) {
            when(userRepository.findByEmail("target" + i + "@example.com")).thenReturn(Optional.empty());
            LoginRequest req = new LoginRequest("target" + i + "@example.com", "Password!");
            mockMvc.perform(post("/api/auth/login")
                            .with(r -> { r.setRemoteAddr(clientIp); return r; })
                            .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                            .header(CSRF_HEADER_NAME, csrfToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        // Attempt 21: Pre-check sees IP counter >= 20 -> Throttled with 429 Too Many Requests
        LoginRequest req21 = new LoginRequest("target21@example.com", "Password!");
        mockMvc.perform(post("/api/auth/login")
                        .with(r -> { r.setRemoteAddr(clientIp); return r; })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req21)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));
    }

    // =========================================================================
    // 10. Database Lookup Skipped When IP Is Throttled
    // =========================================================================

    @Test
    void shouldNotPerformDatabaseLookupWhenIpIsThrottled() throws Exception {
        String clientIp = "10.0.0.11";
        String ipKey = loginRateLimiter.buildIpKey(clientIp);

        // Pre-throttle the IP directly in Redis
        redisTemplate.opsForValue().set(ipKey, "20", 300, TimeUnit.SECONDS);

        clearInvocations(userRepository);

        LoginRequest request = new LoginRequest("ratelimit.user@example.com", "ValidPassword123!");
        mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr(clientIp); return req; })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests());

        // Verify findByEmail was NEVER executed
        verify(userRepository, never()).findByEmail(anyString());
    }

    // =========================================================================
    // 11. Password Matching Skipped When Rate Limited
    // =========================================================================

    @Test
    void shouldNotPerformPasswordMatchingWhenRateLimited() throws Exception {
        String email = "ratelimit.user@example.com";
        String accountKey = loginRateLimiter.buildAccountKey(email);

        // Pre-throttle account directly in Redis
        redisTemplate.opsForValue().set(accountKey, "5", 300, TimeUnit.SECONDS);

        clearInvocations(passwordEncoder);

        LoginRequest request = new LoginRequest(email, "ValidPassword123!");
        mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr("10.0.0.12"); return req; })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests());

        // Verify passwordEncoder.matches was NEVER executed
        verify(passwordEncoder, never()).matches(any(), any());
    }

    // =========================================================================
    // 12. Successful Login for User B Does Not Clear User A Counter
    // =========================================================================

    @Test
    void shouldNotClearAccountCounterForDifferentAccount() throws Exception {
        String clientIp = "10.0.0.13";
        String userAEmail = "victim.a@example.com";
        String userAKey = loginRateLimiter.buildAccountKey(userAEmail);

        when(userRepository.findByEmail(userAEmail)).thenReturn(Optional.empty());

        // Fail 3 times on User A
        LoginRequest badRequestA = new LoginRequest(userAEmail, "WrongPass!");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(req -> { req.setRemoteAddr(clientIp); return req; })
                            .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                            .header(CSRF_HEADER_NAME, csrfToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequestA)))
                    .andExpect(status().isBadRequest());
        }
        assertEquals("3", redisTemplate.opsForValue().get(userAKey));

        // User B successfully logs in
        LoginRequest validRequestB = new LoginRequest("ratelimit.user@example.com", "ValidPassword123!");
        mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr(clientIp); return req; })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequestB)))
                .andExpect(status().isOk());

        // User A's counter must STILL be 3
        assertEquals("3", redisTemplate.opsForValue().get(userAKey),
                "Successful login for User B must not clear User A's failure counter");
    }

    // =========================================================================
    // 13. Bounded Memory: No Account Key Allocation When IP Is Throttled
    // =========================================================================

    @Test
    void shouldNotAllocateAccountKeysWhenIpIsThrottled() throws Exception {
        String clientIp = "10.0.0.14";
        String ipKey = loginRateLimiter.buildIpKey(clientIp);

        // Pre-throttle IP in Redis
        redisTemplate.opsForValue().set(ipKey, "20", 300, TimeUnit.SECONDS);

        String randomEmail = "unseen.account@example.com";
        String randomAccountKey = loginRateLimiter.buildAccountKey(randomEmail);

        LoginRequest request = new LoginRequest(randomEmail, "RandomPassword!");
        mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr(clientIp); return req; })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests());

        // Account key must NOT be created in Redis (IP-first admission control)
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(randomAccountKey)),
                "Account key must not be allocated when IP is already throttled");
    }

    // =========================================================================
    // 14. Session Fixation & Auth State Preservation on Failed Login
    // =========================================================================

    @Test
    void shouldNotAuthenticateOrRotateSessionOnFailedLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String initialSessionId = session.getId();

        LoginRequest badRequest = new LoginRequest("ratelimit.user@example.com", "WrongPassword!");
        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest());

        assertEquals(initialSessionId, session.getId());
        assertNull(session.getAttribute(AuthService.SESSION_USER_ID_KEY));
    }

    // =========================================================================
    // 15. Session ID Rotation and Authentication on Successful Login
    // =========================================================================

    @Test
    void shouldRotateSessionIdAndAuthenticateOnSuccessfulLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String initialSessionId = session.getId();

        LoginRequest validRequest = new LoginRequest("ratelimit.user@example.com", "ValidPassword123!");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession postLoginSession = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(postLoginSession);
        assertNotEquals(initialSessionId, postLoginSession.getId(), "Session ID must be rotated on login");
        assertEquals(validUser.getId(), postLoginSession.getAttribute(AuthService.SESSION_USER_ID_KEY));
    }

    // =========================================================================
    // 16. CSRF Token Enforcement
    // =========================================================================

    @Test
    void shouldRejectLoginWithoutCsrfToken() throws Exception {
        LoginRequest request = new LoginRequest("ratelimit.user@example.com", "ValidPassword123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 17. SHA-256 Hashed Identifier & No Sensitive Data in Redis
    // =========================================================================

    @Test
    void shouldStoreSha256HashedEmailInRedisAndNoSensitiveData() throws Exception {
        String email = "plaintext.sensitive.email@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        LoginRequest badRequest = new LoginRequest(email, "SecretPlainTextPassword123!");

        mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr("10.0.0.15"); return req; })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest());

        String accountKey = loginRateLimiter.buildAccountKey(email);
        assertTrue(accountKey.startsWith("gitracker:auth:login:account:"));
        String hashPart = accountKey.substring("gitracker:auth:login:account:".length());
        assertEquals(64, hashPart.length(), "Hash suffix must be a 64-character SHA-256 hex string");

        // Verify no plaintext email key exists
        Set<String> emailMatchingKeys = redisTemplate.keys("*" + email + "*");
        assertTrue(emailMatchingKeys == null || emailMatchingKeys.isEmpty(),
                "Plaintext email must never appear in Redis key names");

        // Verify no password in key value
        String counterVal = redisTemplate.opsForValue().get(accountKey);
        assertEquals("1", counterVal);
    }

    // =========================================================================
    // 18. Redis Failure Semantics (All Three Operations Under FAIL_CLOSED)
    // =========================================================================

    @Test
    void shouldHandleRedisFailureAccordingToConfiguredSecurityPolicy() {
        StringRedisTemplate failingRedis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(failingRedis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenThrow(new RedisConnectionFailureException("Redis connection refused"));
        when(failingRedis.execute(any(), any(), any())).thenThrow(new RedisConnectionFailureException("Redis connection refused"));
        doThrow(new RedisConnectionFailureException("Redis connection refused")).when(failingRedis).delete(anyString());

        LoginRateLimiter failClosedLimiter = new LoginRateLimiter(
                failingRedis, true, 5, 300, 20, 300, "FAIL_CLOSED"
        );

        // 1. checkRateLimit() failure under FAIL_CLOSED -> protective RateLimitExceededException (429 fallback)
        RateLimitExceededException ex1 = assertThrows(RateLimitExceededException.class, () ->
                failClosedLimiter.checkRateLimit("user@example.com", "10.0.0.16"));
        assertEquals(60L, ex1.getRetryAfterSeconds());

        // 2. recordFailedAttempt() failure under FAIL_CLOSED -> protective RateLimitExceededException (429 fallback)
        RateLimitExceededException ex2 = assertThrows(RateLimitExceededException.class, () ->
                failClosedLimiter.recordFailedAttempt("user@example.com", "10.0.0.16"));
        assertEquals(60L, ex2.getRetryAfterSeconds());

        // 3. recordSuccessfulLogin() failure under FAIL_CLOSED -> best-effort cleanup, does NOT throw, auth proceeds
        assertDoesNotThrow(() ->
                failClosedLimiter.recordSuccessfulLogin("user@example.com"));
    }

    // =========================================================================
    // 19. Recovery After Throttle Window Expires (Temporary Throttling, Not Permanent Lockout)
    // =========================================================================

    @Test
    void shouldAllowRecoveryAfterThrottleWindowExpires() throws Exception {
        String clientIp = "10.0.0.17";
        String email = "ratelimit.user@example.com";
        String accountKey = loginRateLimiter.buildAccountKey(email);
        LoginRequest badRequest = new LoginRequest(email, "WrongPass!");

        // 5 failures -> throttled
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(req -> { req.setRemoteAddr(clientIp); return req; })
                            .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                            .header(CSRF_HEADER_NAME, csrfToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isBadRequest());
        }

        // 6th attempt is throttled
        mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr(clientIp); return req; })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isTooManyRequests());

        // Simulate TTL expiration by deleting account key
        redisTemplate.delete(accountKey);

        // Next login attempt with valid credentials succeeds immediately
        LoginRequest validRequest = new LoginRequest(email, "ValidPassword123!");
        mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr(clientIp); return req; })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(validUser.getId()));
    }

    // =========================================================================
    // 20. Malformed Account Counter Triggers FAIL_CLOSED (HTTP 429)
    // =========================================================================

    @Test
    void shouldFailClosedWhenRedisCounterIsMalformed() throws Exception {
        String email = "ratelimit.user@example.com";
        String accountKey = loginRateLimiter.buildAccountKey(email);

        // Corrupt the account counter in Redis with invalid string
        redisTemplate.opsForValue().set(accountKey, "not-a-number", 300, TimeUnit.SECONDS);

        LoginRequest request = new LoginRequest(email, "ValidPassword123!");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr("10.0.0.18"); return req; })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.retryAfterSeconds").value(60))
                .andReturn();

        // Verify NumberFormatException and stack trace details are NOT leaked to the client
        String responseBody = result.getResponse().getContentAsString();
        assertFalse(responseBody.contains("NumberFormatException"));
        assertFalse(responseBody.contains("not-a-number"));
        assertFalse(responseBody.contains("Exception"));
    }

    // =========================================================================
    // 21. Malformed IP Counter Triggers FAIL_CLOSED and Skips DB/Auth Lookup
    // =========================================================================

    @Test
    void shouldFailClosedWhenIpCounterIsMalformed() throws Exception {
        String clientIp = "10.0.0.19";
        String ipKey = loginRateLimiter.buildIpKey(clientIp);

        // Corrupt the IP counter in Redis
        redisTemplate.opsForValue().set(ipKey, "invalid-ip-counter", 300, TimeUnit.SECONDS);

        clearInvocations(userRepository);
        clearInvocations(passwordEncoder);

        LoginRequest request = new LoginRequest("ratelimit.user@example.com", "ValidPassword123!");
        mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr(clientIp); return req; })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.status").value(429));

        // Verify DB lookup and password matching are completely bypassed
        verify(userRepository, never()).findByEmail(anyString());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    // =========================================================================
    // 22. Negative Counter Values Treated as Malformed State (HTTP 429)
    // =========================================================================

    @Test
    void shouldFailClosedWhenCounterIsNegative() throws Exception {
        String email = "ratelimit.user@example.com";
        String accountKey = loginRateLimiter.buildAccountKey(email);

        // Set invalid negative counter in Redis
        redisTemplate.opsForValue().set(accountKey, "-1", 300, TimeUnit.SECONDS);

        LoginRequest request = new LoginRequest(email, "ValidPassword123!");
        mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr("10.0.0.20"); return req; })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.status").value(429));
    }

    // =========================================================================
    // 23. FAIL_OPEN Behavior on Malformed Counter State
    // =========================================================================

    @Test
    void shouldAllowLoginWhenFailOpenIsConfiguredAndCounterIsMalformed() {
        StringRedisTemplate mockRedis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(mockRedis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn("malformed-data");

        LoginRateLimiter failOpenLimiter = new LoginRateLimiter(
                mockRedis, true, 5, 300, 20, 300, "FAIL_OPEN"
        );

        // FAIL_OPEN allows checkRateLimit and recordFailedAttempt to proceed without throwing
        assertDoesNotThrow(() ->
                failOpenLimiter.checkRateLimit("user@example.com", "10.0.0.21"));
        assertDoesNotThrow(() ->
                failOpenLimiter.recordFailedAttempt("user@example.com", "10.0.0.21"));
    }
}
