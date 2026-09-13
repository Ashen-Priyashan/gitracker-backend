package com.gitracker.service;

import com.gitracker.dto.LoginRequest;
import com.gitracker.dto.SignUpRequest;
import com.gitracker.dto.UserAuthResponse;
import com.gitracker.entity.UserEntity;
import com.gitracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder);
    }

    @Test
    void shouldRegisterNewUserSuccessfully() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        httpRequest.setSession(session);
        String originalSessionId = session.getId();

        SignUpRequest request = new SignUpRequest("Ashen", "ashen@example.com", "secret123", "secret123");

        when(userRepository.existsByEmail("ashen@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$hashedsecret");

        UserEntity savedUser = UserEntity.builder()
                .id(1L)
                .name("Ashen")
                .email("ashen@example.com")
                .password("$2a$10$hashedsecret")
                .build();
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);

        UserAuthResponse response = authService.signUp(request, httpRequest);

        assertNotNull(response);
        assertEquals("ashen@example.com", response.email());
        assertNotEquals(originalSessionId, session.getId(), "Session ID must rotate on signup");
        assertEquals(1L, session.getAttribute(AuthService.SESSION_USER_ID_KEY));
    }

    @Test
    void shouldThrowExceptionWhenEmailAlreadyExists() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        httpRequest.setSession(session);
        String originalSessionId = session.getId();

        SignUpRequest request = new SignUpRequest("Ashen", "ashen@example.com", "secret123", "secret123");

        when(userRepository.existsByEmail("ashen@example.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.signUp(request, httpRequest));
        assertEquals(originalSessionId, session.getId(), "Session ID must not rotate on failed signup");
        assertNull(session.getAttribute(AuthService.SESSION_USER_ID_KEY));
    }

    @Test
    void shouldLoginSuccessfullyWithValidPassword() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        httpRequest.setSession(session);
        String originalSessionId = session.getId();

        LoginRequest request = new LoginRequest("ashen@example.com", "secret123");

        UserEntity user = UserEntity.builder()
                .id(1L)
                .name("Ashen")
                .email("ashen@example.com")
                .password("$2a$10$hashedsecret")
                .build();

        when(userRepository.findByEmail("ashen@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "$2a$10$hashedsecret")).thenReturn(true);

        UserAuthResponse response = authService.login(request, httpRequest);

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertNotEquals(originalSessionId, session.getId(), "Session ID must rotate on login");
        assertEquals(1L, session.getAttribute(AuthService.SESSION_USER_ID_KEY));
    }

    @Test
    void shouldThrowExceptionOnInvalidPassword() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        httpRequest.setSession(session);
        String originalSessionId = session.getId();

        LoginRequest request = new LoginRequest("ashen@example.com", "wrongpass");

        UserEntity user = UserEntity.builder()
                .id(1L)
                .email("ashen@example.com")
                .password("$2a$10$hashedsecret")
                .build();

        when(userRepository.findByEmail("ashen@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", "$2a$10$hashedsecret")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> authService.login(request, httpRequest));
        assertEquals(originalSessionId, session.getId(), "Session ID must not rotate on failed login");
        assertNull(session.getAttribute(AuthService.SESSION_USER_ID_KEY));
    }

    @Test
    void shouldLinkGithubUsernameSuccessfully() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_ID_KEY, 1L);

        UserEntity user = UserEntity.builder()
                .id(1L)
                .name("Ashen")
                .email("ashen@example.com")
                .password("$2a$10$hashedsecret")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserAuthResponse response = authService.linkGithubUsername("octocat", session);

        assertNotNull(response);
        assertTrue(response.githubConnected());
        assertEquals("octocat", response.githubUsername());
        assertEquals("https://github.com/octocat.png", response.githubAvatarUrl());
        assertEquals("octocat", session.getAttribute("CONNECTED_GITHUB_USERNAME"));
    }
}
