package com.gitracker.service;

import com.gitracker.client.GitHubApiClient;
import com.gitracker.dto.GitHubConnectionStatusResponse;
import com.gitracker.entity.GitHubAppConnection;
import com.gitracker.repository.GitHubAppConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubAuthServiceTest {

    @Mock
    private GitHubApiClient gitHubApiClient;

    @Mock
    private GitHubAppConnectionRepository connectionRepository;

    @Mock
    private com.gitracker.repository.UserRepository userRepository;

    private GitHubAuthService gitHubAuthService;

    @BeforeEach
    void setUp() {
        gitHubAuthService = new GitHubAuthService(
                "test-client-id",
                "test-client-secret",
                "http://localhost:8080/api/github/callback",
                "http://localhost:5173",
                gitHubApiClient,
                connectionRepository,
                userRepository
        );
    }

    @Test
    void shouldGenerateAuthorizationUrlAndSetStateInSession() {
        MockHttpSession session = new MockHttpSession();

        String url = gitHubAuthService.generateAuthorizationUrl(session);

        assertNotNull(url);
        assertTrue(url.contains("client_id=test-client-id"));
        assertTrue(url.contains("redirect_uri=http://localhost:8080/api/github/callback"));
        assertNotNull(session.getAttribute("GITHUB_OAUTH_STATE"));
    }

    @Test
    void shouldReturnNotConnectedWhenNoUserInSession() {
        MockHttpSession session = new MockHttpSession();

        GitHubConnectionStatusResponse status = gitHubAuthService.getConnectionStatus(session);

        assertFalse(status.connected());
        assertNull(status.username());
    }

    @Test
    void shouldReturnConnectedWhenUserInSessionAndDb() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("CONNECTED_GITHUB_USERNAME", "octocat");

        GitHubAppConnection connection = GitHubAppConnection.builder()
                .githubId(12345L)
                .username("octocat")
                .avatarUrl("https://avatar.url")
                .connectedAt(LocalDateTime.now())
                .build();

        when(connectionRepository.findByUsername("octocat")).thenReturn(Optional.of(connection));

        GitHubConnectionStatusResponse status = gitHubAuthService.getConnectionStatus(session);

        assertTrue(status.connected());
        assertEquals("octocat", status.username());
        assertEquals("https://avatar.url", status.avatarUrl());
    }

    @Test
    void shouldDisconnectAndRemoveSessionAndDbRecord() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("CONNECTED_GITHUB_USERNAME", "octocat");

        GitHubAppConnection connection = GitHubAppConnection.builder()
                .githubId(12345L)
                .username("octocat")
                .build();

        when(connectionRepository.findByUsername("octocat")).thenReturn(Optional.of(connection));

        gitHubAuthService.disconnect(session);

        verify(connectionRepository).delete(connection);
        assertNull(session.getAttribute("CONNECTED_GITHUB_USERNAME"));
    }
}
