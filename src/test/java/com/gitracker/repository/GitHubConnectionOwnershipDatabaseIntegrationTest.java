package com.gitracker.repository;

import com.gitracker.client.GitHubApiClient;
import com.gitracker.dto.GitHubUserDto;
import com.gitracker.entity.GitHubAppConnection;
import com.gitracker.entity.UserEntity;
import com.gitracker.service.AuthService;
import com.gitracker.service.GitHubAuthService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5435/gitracker_test_migration",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=update"
})
class GitHubConnectionOwnershipDatabaseIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private GitHubAppConnectionRepository connectionRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private GitHubApiClient gitHubApiClient;

    private MockRestServiceServer oauthServer;
    private GitHubAuthService gitHubAuthService;

    @BeforeEach
    void setUp() {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();

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
    void shouldPreventDuplicateGithubIdInGitHubAppConnections() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.execute(status -> {
            GitHubAppConnection conn1 = GitHubAppConnection.builder()
                    .githubId(99001L)
                    .username("user1")
                    .accessToken("tok1")
                    .build();
            return connectionRepository.saveAndFlush(conn1);
        });

        assertThrows(DataIntegrityViolationException.class, () -> {
            tx.execute(status -> {
                GitHubAppConnection conn2 = GitHubAppConnection.builder()
                        .githubId(99001L) // Duplicate githubId
                        .username("user2")
                        .accessToken("tok2")
                        .build();
                return connectionRepository.saveAndFlush(conn2);
            });
        });
    }

    @Test
    void shouldPreventMultipleUsersReferencingSameGitHubConnection() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Long savedConnId = tx.execute(status -> {
            GitHubAppConnection conn = GitHubAppConnection.builder()
                    .githubId(99002L)
                    .username("shared-user")
                    .accessToken("tok-shared")
                    .build();
            return connectionRepository.saveAndFlush(conn).getId();
        });

        tx.execute(status -> {
            GitHubAppConnection connA = connectionRepository.findById(savedConnId).orElseThrow();
            UserEntity userA = UserEntity.builder()
                    .name("User A")
                    .email("usera@test.com")
                    .password("secret123")
                    .githubConnection(connA)
                    .build();
            return userRepository.saveAndFlush(userA);
        });

        // Attempting to attach the same connection to User B must violate unique constraint on users(github_connection_id)
        assertThrows(DataIntegrityViolationException.class, () -> {
            tx.execute(status -> {
                GitHubAppConnection connB = connectionRepository.findById(savedConnId).orElseThrow();
                UserEntity userB = UserEntity.builder()
                        .name("User B")
                        .email("userb@test.com")
                        .password("secret456")
                        .githubConnection(connB)
                        .build();
                return userRepository.saveAndFlush(userB);
            });
        });
    }

    @Test
    void shouldAllowMultipleUsersWithNullGitHubConnection() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.execute(status -> {
            UserEntity userA = UserEntity.builder()
                    .name("User Without Connection 1")
                    .email("noconn1@test.com")
                    .password("secret")
                    .githubConnection(null)
                    .build();
            UserEntity userB = UserEntity.builder()
                    .name("User Without Connection 2")
                    .email("noconn2@test.com")
                    .password("secret")
                    .githubConnection(null)
                    .build();

            userRepository.saveAndFlush(userA);
            userRepository.saveAndFlush(userB);
            return null;
        });

        assertEquals(2, userRepository.count());
    }

    @Test
    void shouldRejectCrossUserAccountLinkingAndPreserveOriginalOwnerAndToken() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // Setup: User A owns GitHub account 123456
        Long[] userIds = tx.execute(status -> {
            GitHubAppConnection connA = GitHubAppConnection.builder()
                    .githubId(123456L)
                    .username("legitimate-owner")
                    .accessToken("original_owner_super_secret_token")
                    .connectedAt(LocalDateTime.now())
                    .build();
            GitHubAppConnection savedConnA = connectionRepository.saveAndFlush(connA);

            UserEntity userA = UserEntity.builder()
                    .name("User A")
                    .email("owner_a@test.com")
                    .password("passwordA")
                    .githubUsername("legitimate-owner")
                    .githubConnection(savedConnA)
                    .build();
            UserEntity savedUserA = userRepository.saveAndFlush(userA);

            UserEntity userB = UserEntity.builder()
                    .name("User B")
                    .email("attacker_b@test.com")
                    .password("passwordB")
                    .build();
            UserEntity savedUserB = userRepository.saveAndFlush(userB);

            return new Long[]{savedUserA.getId(), savedUserB.getId()};
        });

        Long userAId = userIds[0];
        Long userBId = userIds[1];

        // Scenario: User B attempts OAuth callback with GitHub account 123456
        MockHttpSession sessionB = new MockHttpSession();
        sessionB.setAttribute(AuthService.SESSION_USER_ID_KEY, userBId);
        sessionB.setAttribute("GITHUB_OAUTH_STATE", "oauth-state-user-b");

        oauthServer.expect(requestTo("https://github.com/login/oauth/access_token"))
                .andRespond(withSuccess("{\"access_token\":\"temp_token_b\",\"token_type\":\"bearer\",\"scope\":\"repo,read:user\"}", MediaType.APPLICATION_JSON));
        GitHubUserDto gitHubUser = new GitHubUserDto(123456L, "legitimate-owner", "Legit Owner", null, null, 0, 0, 0, "https://avatar.url", null);
        when(gitHubApiClient.getAuthenticatedUser("temp_token_b")).thenReturn(gitHubUser);

        // Execute callback
        String redirect = gitHubAuthService.handleCallback("auth-code-b", "oauth-state-user-b", sessionB);

        // Invariant C: Must be rejected with account_already_linked error
        assertEquals("http://localhost:5173/settings?error=account_already_linked", redirect);
        assertNull(sessionB.getAttribute("CONNECTED_GITHUB_USERNAME"));

        // Verify database state: User A still owns the connection and the token is unchanged
        tx.execute(status -> {
            UserEntity reloadedUserA = userRepository.findById(userAId).orElseThrow();
            assertNotNull(reloadedUserA.getGithubConnection());
            assertEquals(123456L, reloadedUserA.getGithubConnection().getGithubId());
            assertEquals("original_owner_super_secret_token", reloadedUserA.getGithubConnection().getAccessToken());

            UserEntity reloadedUserB = userRepository.findById(userBId).orElseThrow();
            assertNull(reloadedUserB.getGithubConnection(), "User B must not have any connection attached");
            assertNull(reloadedUserB.getGithubUsername());
            return null;
        });
    }

    @Test
    void shouldAllowLegitimateOwnerToReconnectAndRefreshToken() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Long userAId = tx.execute(status -> {
            GitHubAppConnection connA = GitHubAppConnection.builder()
                    .githubId(654321L)
                    .username("same-user")
                    .accessToken("old_token_before_refresh")
                    .connectedAt(LocalDateTime.now())
                    .build();
            GitHubAppConnection savedConnA = connectionRepository.saveAndFlush(connA);

            UserEntity userA = UserEntity.builder()
                    .name("User Reconnecting")
                    .email("reconnect@test.com")
                    .password("pass123")
                    .githubUsername("same-user")
                    .githubConnection(savedConnA)
                    .build();
            return userRepository.saveAndFlush(userA).getId();
        });

        MockHttpSession sessionA = new MockHttpSession();
        sessionA.setAttribute(AuthService.SESSION_USER_ID_KEY, userAId);
        sessionA.setAttribute("GITHUB_OAUTH_STATE", "oauth-state-reconnect");

        oauthServer.expect(requestTo("https://github.com/login/oauth/access_token"))
                .andRespond(withSuccess("{\"access_token\":\"new_refreshed_token\",\"token_type\":\"bearer\",\"scope\":\"repo,read:user\"}", MediaType.APPLICATION_JSON));
        GitHubUserDto gitHubUser = new GitHubUserDto(654321L, "same-user", "Same User", null, null, 0, 0, 0, "https://avatar.url", null);
        when(gitHubApiClient.getAuthenticatedUser("new_refreshed_token")).thenReturn(gitHubUser);

        String redirect = gitHubAuthService.handleCallback("reconnect-code", "oauth-state-reconnect", sessionA);

        assertEquals("http://localhost:5173/settings?connected=true", redirect);
        assertEquals("same-user", sessionA.getAttribute("CONNECTED_GITHUB_USERNAME"));

        tx.execute(status -> {
            UserEntity reloaded = userRepository.findById(userAId).orElseThrow();
            assertNotNull(reloaded.getGithubConnection());
            assertEquals("new_refreshed_token", reloaded.getGithubConnection().getAccessToken());
            return null;
        });
    }
}
