package com.gitracker.service;

import com.gitracker.client.GitHubApiClient;
import com.gitracker.dto.GitHubConnectionStatusResponse;
import com.gitracker.dto.GitHubUserDto;
import com.gitracker.entity.GitHubAppConnection;
import com.gitracker.entity.UserEntity;
import com.gitracker.repository.GitHubAppConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class GitHubAuthServiceTest {

    @Mock
    private GitHubApiClient gitHubApiClient;

    @Mock
    private GitHubAppConnectionRepository connectionRepository;

    @Mock
    private com.gitracker.repository.UserRepository userRepository;

    private MockRestServiceServer oauthServer;
    private GitHubAuthService gitHubAuthService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://github.com");
        oauthServer = MockRestServiceServer.bindTo(builder).build();
        gitHubAuthService = new GitHubAuthService(
                "test-client-id",
                "test-client-secret",
                "http://localhost:8080/api/github/callback",
                "http://localhost:5173",
                gitHubApiClient,
                connectionRepository,
                userRepository,
                builder.build()
        );
    }

    @Test
    void shouldGenerateAuthorizationUrlAndSetStateInSession() {
        MockHttpSession session = new MockHttpSession();

        String url = gitHubAuthService.generateAuthorizationUrl(session);

        assertNotNull(url);
        assertTrue(url.contains("client_id=test-client-id"));
        assertTrue(url.contains("redirect_uri="));
        assertNotNull(session.getAttribute("GITHUB_OAUTH_STATE"));
    }

    @Test
    void shouldEncodeSpecialCharactersInRedirectUri() {
        GitHubAuthService serviceWithSpecialChars = new GitHubAuthService(
                "client-123",
                "secret",
                "http://localhost:8080/api/callback?param=1&foo=bar:baz",
                "http://localhost:5173",
                gitHubApiClient,
                connectionRepository,
                userRepository
        );
        MockHttpSession session = new MockHttpSession();
        String url = serviceWithSpecialChars.generateAuthorizationUrl(session);

        assertNotNull(url);
        assertTrue(url.startsWith("https://github.com/login/oauth/authorize?client_id=client-123&redirect_uri="));
        // Verify query separators within the redirect_uri value were encoded and not treated as outer query separators
        assertFalse(url.contains("?param=1&foo="), "Query params inside redirect_uri must be URL-encoded");
    }

    @Test
    void shouldThrowExceptionWhenClientIdIsEmpty() {
        GitHubAuthService emptyClientService = new GitHubAuthService(
                "",
                "secret",
                "http://localhost:8080/api/github/callback",
                "http://localhost:5173",
                gitHubApiClient,
                connectionRepository,
                userRepository
        );
        MockHttpSession session = new MockHttpSession();

        assertThrows(IllegalStateException.class, () -> emptyClientService.generateAuthorizationUrl(session));
    }

    @Test
    void shouldReturnConnectedWhenUserHasDirectGithubUsername() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_ID_KEY, 1L);

        com.gitracker.entity.UserEntity user = com.gitracker.entity.UserEntity.builder()
                .id(1L)
                .name("Test User")
                .email("test@example.com")
                .password("password")
                .githubUsername("directuser")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        GitHubConnectionStatusResponse status = gitHubAuthService.getConnectionStatus(session);

        assertTrue(status.connected());
        assertEquals("directuser", status.username());
        assertEquals("https://github.com/directuser.png", status.avatarUrl());
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

        LocalDateTime now = LocalDateTime.now();
        GitHubAppConnection connection = GitHubAppConnection.builder()
                .githubId(12345L)
                .username("octocat")
                .avatarUrl("https://avatar.url")
                .connectedAt(now)
                .lastSyncedAt(now)
                .build();

        when(connectionRepository.findByUsername("octocat")).thenReturn(Optional.of(connection));

        GitHubConnectionStatusResponse status = gitHubAuthService.getConnectionStatus(session);

        assertTrue(status.connected());
        assertEquals("octocat", status.username());
        assertEquals("https://avatar.url", status.avatarUrl());
        assertEquals(now.toString(), status.lastSyncedAt());
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

    @Test
    void shouldRejectCallbackWhenOAuthStateIsMissing() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("GITHUB_OAUTH_STATE", "valid-state-123");

        String redirect = gitHubAuthService.handleCallback("code-123", null, session);

        assertEquals("http://localhost:5173/settings?error=invalid_state", redirect);
        assertEquals("valid-state-123", session.getAttribute("GITHUB_OAUTH_STATE"), "State must remain if mismatched");
        verifyNoInteractions(gitHubApiClient);
    }

    @Test
    void shouldRejectCallbackWhenOAuthStateIsMismatched() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("GITHUB_OAUTH_STATE", "valid-state-123");

        String redirect = gitHubAuthService.handleCallback("code-123", "attacker-state", session);

        assertEquals("http://localhost:5173/settings?error=invalid_state", redirect);
        verifyNoInteractions(gitHubApiClient);
    }

    @Test
    void shouldRemoveStateAndPreventReplayAttacks() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("GITHUB_OAUTH_STATE", "single-use-state");

        // First callback with blank code validates and removes state from session
        String firstRedirect = gitHubAuthService.handleCallback("", "single-use-state", session);
        assertEquals("http://localhost:5173/settings?error=missing_code", firstRedirect);
        assertNull(session.getAttribute("GITHUB_OAUTH_STATE"), "State must be removed after validation");

        // Replay attempt with same state must fail because state is now absent
        String replayRedirect = gitHubAuthService.handleCallback("new-code", "single-use-state", session);
        assertEquals("http://localhost:5173/settings?error=invalid_state", replayRedirect);
    }

    @Test
    void shouldCreateNewGitHubConnectionForCurrentUser() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_ID_KEY, 1L);
        session.setAttribute("GITHUB_OAUTH_STATE", "valid-state");

        UserEntity user = UserEntity.builder()
                .id(1L)
                .name("User A")
                .email("usera@example.com")
                .build();

        oauthServer.expect(requestTo("https://github.com/login/oauth/access_token"))
                .andRespond(withSuccess("{\"access_token\":\"gho_token_123\",\"token_type\":\"bearer\",\"scope\":\"repo,read:user\"}", MediaType.APPLICATION_JSON));
        GitHubUserDto gitHubUser = new GitHubUserDto(12345L, "octocat", "Octo Cat", null, null, 0, 0, 0, "https://avatar.url", null);
        when(gitHubApiClient.getAuthenticatedUser("gho_token_123")).thenReturn(gitHubUser);

        when(userRepository.findByGithubConnection_GithubId(12345L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(connectionRepository.findByGithubId(12345L)).thenReturn(Optional.empty());
        when(connectionRepository.save(any(GitHubAppConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        String redirect = gitHubAuthService.handleCallback("valid-code", "valid-state", session);

        assertEquals("http://localhost:5173/settings?connected=true", redirect);
        assertNotNull(user.getGithubConnection());
        assertEquals(12345L, user.getGithubConnection().getGithubId());
        assertEquals("gho_token_123", user.getGithubConnection().getAccessToken());
        assertEquals("octocat", user.getGithubUsername());
        verify(userRepository).save(user);
        assertEquals("octocat", session.getAttribute("CONNECTED_GITHUB_USERNAME"));
    }

    @Test
    void shouldAllowCurrentOwnerToReconnectOwnGitHubAccount() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_ID_KEY, 1L);
        session.setAttribute("GITHUB_OAUTH_STATE", "valid-state");

        GitHubAppConnection existingConn = GitHubAppConnection.builder()
                .id(100L)
                .githubId(12345L)
                .username("octocat")
                .accessToken("old_token")
                .build();

        UserEntity user = UserEntity.builder()
                .id(1L)
                .name("User A")
                .email("usera@example.com")
                .githubConnection(existingConn)
                .build();

        oauthServer.expect(requestTo("https://github.com/login/oauth/access_token"))
                .andRespond(withSuccess("{\"access_token\":\"gho_new_token\",\"token_type\":\"bearer\",\"scope\":\"repo,read:user\"}", MediaType.APPLICATION_JSON));
        GitHubUserDto gitHubUser = new GitHubUserDto(12345L, "octocat", "Octo Cat", null, null, 0, 0, 0, "https://avatar.url", null);
        when(gitHubApiClient.getAuthenticatedUser("gho_new_token")).thenReturn(gitHubUser);

        when(userRepository.findByGithubConnection_GithubId(12345L)).thenReturn(Optional.of(user));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(connectionRepository.findByGithubId(12345L)).thenReturn(Optional.of(existingConn));
        when(connectionRepository.save(any(GitHubAppConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        String redirect = gitHubAuthService.handleCallback("reconnect-code", "valid-state", session);

        assertEquals("http://localhost:5173/settings?connected=true", redirect);
        assertEquals("gho_new_token", existingConn.getAccessToken(), "Legitimate owner should update token");
        verify(connectionRepository).save(existingConn);
        verify(userRepository).save(user);
    }

    @Test
    void shouldRejectLinkingGitHubAccountAlreadyOwnedByAnotherUser() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_ID_KEY, 2L); // User B is attempting to link
        session.setAttribute("GITHUB_OAUTH_STATE", "valid-state");

        GitHubAppConnection userAConn = GitHubAppConnection.builder()
                .id(100L)
                .githubId(12345L)
                .username("octocat")
                .accessToken("user_a_secret_token")
                .build();

        UserEntity userA = UserEntity.builder()
                .id(1L)
                .name("User A")
                .email("usera@example.com")
                .githubConnection(userAConn)
                .build();

        oauthServer.expect(requestTo("https://github.com/login/oauth/access_token"))
                .andRespond(withSuccess("{\"access_token\":\"temp_token\",\"token_type\":\"bearer\",\"scope\":\"repo,read:user\"}", MediaType.APPLICATION_JSON));
        GitHubUserDto gitHubUser = new GitHubUserDto(12345L, "octocat", "Octo Cat", null, null, 0, 0, 0, "https://avatar.url", null);
        when(gitHubApiClient.getAuthenticatedUser("temp_token")).thenReturn(gitHubUser);

        // User A is the registered owner of this GitHub account
        when(userRepository.findByGithubConnection_GithubId(12345L)).thenReturn(Optional.of(userA));

        String redirect = gitHubAuthService.handleCallback("attacker-code", "valid-state", session);

        assertEquals("http://localhost:5173/settings?error=account_already_linked", redirect);
        assertEquals("user_a_secret_token", userAConn.getAccessToken(), "Original owner's token must not be altered");
        assertEquals(userAConn, userA.getGithubConnection(), "Original owner's connection must remain unchanged");
        verify(userRepository, never()).save(any(UserEntity.class));
        verify(connectionRepository, never()).save(any(GitHubAppConnection.class));
        assertNull(session.getAttribute("CONNECTED_GITHUB_USERNAME"), "User B session must not receive connection");
    }

    @Test
    void shouldRejectLinkingWhenUnauthenticatedAndGitHubAccountAlreadyOwned() {
        MockHttpSession session = new MockHttpSession(); // No authenticated user in session
        session.setAttribute("GITHUB_OAUTH_STATE", "valid-state");

        GitHubAppConnection userAConn = GitHubAppConnection.builder()
                .id(100L)
                .githubId(12345L)
                .username("octocat")
                .accessToken("user_a_secret_token")
                .build();

        UserEntity userA = UserEntity.builder()
                .id(1L)
                .name("User A")
                .email("usera@example.com")
                .githubConnection(userAConn)
                .build();

        oauthServer.expect(requestTo("https://github.com/login/oauth/access_token"))
                .andRespond(withSuccess("{\"access_token\":\"temp_token\",\"token_type\":\"bearer\",\"scope\":\"repo,read:user\"}", MediaType.APPLICATION_JSON));
        GitHubUserDto gitHubUser = new GitHubUserDto(12345L, "octocat", "Octo Cat", null, null, 0, 0, 0, "https://avatar.url", null);
        when(gitHubApiClient.getAuthenticatedUser("temp_token")).thenReturn(gitHubUser);

        when(userRepository.findByGithubConnection_GithubId(12345L)).thenReturn(Optional.of(userA));

        String redirect = gitHubAuthService.handleCallback("anon-code", "valid-state", session);

        assertEquals("http://localhost:5173/settings?error=account_already_linked", redirect);
        assertEquals("user_a_secret_token", userAConn.getAccessToken());
        verify(connectionRepository, never()).save(any(GitHubAppConnection.class));
    }

    @Test
    void shouldCatchDataIntegrityViolationExceptionOnConcurrentLinking() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_ID_KEY, 1L);
        session.setAttribute("GITHUB_OAUTH_STATE", "valid-state");

        oauthServer.expect(requestTo("https://github.com/login/oauth/access_token"))
                .andRespond(withSuccess("{\"access_token\":\"race_token\",\"token_type\":\"bearer\",\"scope\":\"repo,read:user\"}", MediaType.APPLICATION_JSON));
        GitHubUserDto gitHubUser = new GitHubUserDto(12345L, "octocat", "Octo Cat", null, null, 0, 0, 0, "https://avatar.url", null);
        when(gitHubApiClient.getAuthenticatedUser("race_token")).thenReturn(gitHubUser);

        when(userRepository.findByGithubConnection_GithubId(12345L)).thenReturn(Optional.empty());
        when(connectionRepository.findByGithubId(12345L)).thenReturn(Optional.empty());
        when(connectionRepository.save(any(GitHubAppConnection.class)))
                .thenThrow(new DataIntegrityViolationException("uk_github_app_connections_github_id"));

        String redirect = gitHubAuthService.handleCallback("race-code", "valid-state", session);

        assertEquals("http://localhost:5173/settings?error=account_already_linked", redirect);
    }

    @Test
    void shouldPreventUnauthenticatedUserFromDeletingRegisteredUserConnection() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("CONNECTED_GITHUB_USERNAME", "octocat");

        GitHubAppConnection conn = GitHubAppConnection.builder()
                .id(100L)
                .githubId(12345L)
                .username("octocat")
                .build();

        UserEntity userA = UserEntity.builder()
                .id(1L)
                .name("User A")
                .githubConnection(conn)
                .build();

        when(connectionRepository.findByUsername("octocat")).thenReturn(Optional.of(conn));
        when(userRepository.findByGithubConnection(conn)).thenReturn(Optional.of(userA));

        gitHubAuthService.disconnect(session);

        // Deletion must be prevented because the connection belongs to a registered UserEntity
        verify(connectionRepository, never()).delete(any(GitHubAppConnection.class));
        assertNull(session.getAttribute("CONNECTED_GITHUB_USERNAME"));
    }

    @Test
    void shouldAllowRegisteredUserToDisconnectOwnConnection() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_ID_KEY, 1L);

        GitHubAppConnection conn = GitHubAppConnection.builder()
                .id(100L)
                .githubId(12345L)
                .username("octocat")
                .build();

        UserEntity userA = UserEntity.builder()
                .id(1L)
                .name("User A")
                .githubConnection(conn)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(userA));

        gitHubAuthService.disconnect(session);

        verify(connectionRepository).delete(conn);
        assertNull(userA.getGithubConnection());
        verify(userRepository).save(userA);
    }

    @Test
    void shouldPreventUserFromAccessingRepositoriesOfAnotherUsersConnection() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_ID_KEY, 2L);

        UserEntity userB = UserEntity.builder()
                .id(2L)
                .name("User B")
                .githubConnection(null) // User B has no connection
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(userB));

        assertThrows(IllegalStateException.class, () -> gitHubAuthService.getAuthenticatedRepositories(session));
        verifyNoInteractions(gitHubApiClient);
    }
}
