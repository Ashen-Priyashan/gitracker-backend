package com.gitracker.repository;

import com.gitracker.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5435/gitracker_test_migration",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=update"
})
class DatabaseDeduplicationIntegrationTest {

    @Autowired
    private GitHubUserRepository gitHubUserRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private UserAnalyticsRepository userAnalyticsRepository;

    @Autowired
    private CommitRepository commitRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
        ResourceDatabasePopulator initPopulator = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V1__initial_schema.sql"),
                new ClassPathResource("db/migration/V2__sync_users_columns.sql")
        );
        initPopulator.execute(dataSource);
        jdbcTemplate.execute("ALTER TABLE developer_scores ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES users(id);");
    }

    @Test
    void shouldConsolidateDuplicateUsersAndRepointDependentRecordsUsingRealMigrations() {
        Long[] userIds = new Long[3];
        Long[] repoIds = new Long[6];
        Long[] localUserId = new Long[1];

        new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
            // 1. Insert multiple case variants of the same GitHub username into legacy schema
            Long u1Id = jdbcTemplate.queryForObject(
                    "INSERT INTO github_users (github_id, username, name, avatar_url, followers, following, public_repos) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
                    Long.class, 101L, "Ashen-Priyashan", "Ashen Priyashan", "https://example.com/avatar1.png", 10, 5, 2);

            Long u2Id = jdbcTemplate.queryForObject(
                    "INSERT INTO github_users (github_id, username, name, avatar_url, followers, following, public_repos) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
                    Long.class, 101L, "ashen-priyashan", "Ashen Priyashan Lower", "https://example.com/avatar2.png", 10, 5, 2);

            Long u3Id = jdbcTemplate.queryForObject(
                    "INSERT INTO github_users (github_id, username, name, avatar_url, followers, following, public_repos) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
                    Long.class, 101L, "ASHEN-PRIYASHAN", "Ashen Priyashan Upper", "https://example.com/avatar3.png", 10, 5, 2);

            userIds[0] = u1Id;
            userIds[1] = u2Id;
            userIds[2] = u3Id;

            // 2. Insert repositories pointing to different case-variant user IDs
            Long r1 = jdbcTemplate.queryForObject(
                    "INSERT INTO repositories (repo_id, name, github_user_id, stars, forks) VALUES (?, ?, ?, ?, ?) RETURNING id",
                    Long.class, 5001L, "tasklang", u1Id, 5, 1);

            Long r2 = jdbcTemplate.queryForObject(
                    "INSERT INTO repositories (repo_id, name, github_user_id, stars, forks) VALUES (?, ?, ?, ?, ?) RETURNING id",
                    Long.class, 5002L, "microservices-demo", u2Id, 10, 2);

            Long r3Dup = jdbcTemplate.queryForObject(
                    "INSERT INTO repositories (repo_id, name, github_user_id, stars, forks) VALUES (?, ?, ?, ?, ?) RETURNING id",
                    Long.class, 5001L, "tasklang", u3Id, 5, 1);

            // Null repo_id repositories for user1 (should NOT be merged)
            Long r4Null = jdbcTemplate.queryForObject(
                    "INSERT INTO repositories (repo_id, name, github_user_id, stars, forks) VALUES (?, ?, ?, ?, ?) RETURNING id",
                    Long.class, null, "null-repo-1", u1Id, 2, 0);

            Long r5Null = jdbcTemplate.queryForObject(
                    "INSERT INTO repositories (repo_id, name, github_user_id, stars, forks) VALUES (?, ?, ?, ?, ?) RETURNING id",
                    Long.class, null, "null-repo-2", u1Id, 3, 1);

            // Null repo_id repository for different user (user2)
            Long r6NullDiff = jdbcTemplate.queryForObject(
                    "INSERT INTO repositories (repo_id, name, github_user_id, stars, forks) VALUES (?, ?, ?, ?, ?) RETURNING id",
                    Long.class, null, "null-repo-diff-user", u2Id, 4, 1);

            repoIds[0] = r1;
            repoIds[1] = r2;
            repoIds[2] = r3Dup;
            repoIds[3] = r4Null;
            repoIds[4] = r5Null;
            repoIds[5] = r6NullDiff;

            // 3. Insert repository topics (repo1: java, spring; repo3Duplicate: spring, postgresql)
            jdbcTemplate.update("INSERT INTO repository_topics (repository_id, topic) VALUES (?, ?)", r1, "java");
            jdbcTemplate.update("INSERT INTO repository_topics (repository_id, topic) VALUES (?, ?)", r1, "spring");
            jdbcTemplate.update("INSERT INTO repository_topics (repository_id, topic) VALUES (?, ?)", r3Dup, "spring");
            jdbcTemplate.update("INSERT INTO repository_topics (repository_id, topic) VALUES (?, ?)", r3Dup, "postgresql");

            // 4. Insert commits on repo1 and repo3Duplicate
            jdbcTemplate.update("INSERT INTO commits (sha, message, repository_id) VALUES (?, ?, ?)", "commit-sha-101", "Initial commit on repo 1", r1);
            jdbcTemplate.update("INSERT INTO commits (sha, message, repository_id) VALUES (?, ?, ?)", "commit-sha-102", "Commit on duplicate repo 3", r3Dup);

            // 5. Insert issues on repo1 and repo3Duplicate
            jdbcTemplate.update("INSERT INTO issues (issue_number, title, state, repository_id) VALUES (?, ?, ?, ?)", 1L, "Bug in repo 1", "OPEN", r1);
            jdbcTemplate.update("INSERT INTO issues (issue_number, title, state, repository_id) VALUES (?, ?, ?, ?)", 2L, "Bug in repo 3", "CLOSED", r3Dup);

            // 6. Insert pull requests on repo1 and repo3Duplicate
            jdbcTemplate.update("INSERT INTO pull_requests (pr_number, title, state, repository_id) VALUES (?, ?, ?, ?)", 10L, "PR on repo 1", "OPEN", r1);
            jdbcTemplate.update("INSERT INTO pull_requests (pr_number, title, state, repository_id) VALUES (?, ?, ?, ?)", 20L, "PR on repo 3", "MERGED", r3Dup);

            // 7. Insert language statistics on repo1 and repo3Duplicate
            jdbcTemplate.update("INSERT INTO language_statistics (language, bytes, repository_id) VALUES (?, ?, ?)", "Java", 12000L, r1);
            jdbcTemplate.update("INSERT INTO language_statistics (language, bytes, repository_id) VALUES (?, ?, ?)", "Kotlin", 8000L, r3Dup);

            // 8. Insert user_analytics for user1 (yesterday) and user2 (today, more recent)
            jdbcTemplate.update("INSERT INTO user_analytics (github_user_id, total_repositories, total_stars, total_forks, most_used_language, calculated_at) VALUES (?, ?, ?, ?, ?, ?)",
                    u1Id, 1, 5, 1, "Java", java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(1)));

            jdbcTemplate.update("INSERT INTO user_analytics (github_user_id, total_repositories, total_stars, total_forks, most_used_language, calculated_at) VALUES (?, ?, ?, ?, ?, ?)",
                    u2Id, 2, 15, 3, "TypeScript", java.sql.Timestamp.valueOf(LocalDateTime.now()));

            // 9. Insert application user in users table and developer_scores referencing users(id)
            jdbcTemplate.update("INSERT INTO users (name, email, password) VALUES (?, ?, ?)", "Dev User", "dev@example.com", "secretHash");
            Long appUserId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, "dev@example.com");
            localUserId[0] = appUserId;
            jdbcTemplate.update("INSERT INTO developer_scores (user_id, activity_score, quality_score, calculated_at) VALUES (?, ?, ?, ?)",
                    appUserId, 90.0, 80.0, LocalDateTime.now());

            return null;
        });

        Long canonicalUserId = userIds[0];
        Long repo1Id = repoIds[0];
        Long repo2Id = repoIds[1];

        // 10. Execute the actual Flyway V3 migration SQL file
        ResourceDatabasePopulator populatorV3 = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V3__deduplicate_github_users_and_case_insensitive_index.sql")
        );
        populatorV3.execute(dataSource);

        // 11. Verify user consolidation via jdbcTemplate
        Integer survivingUsersCount = jdbcTemplate.queryForObject("SELECT count(*) FROM github_users", Integer.class);
        assertEquals(1, survivingUsersCount, "Only one canonical user should survive");
        Long survivingUserId = jdbcTemplate.queryForObject("SELECT id FROM github_users", Long.class);
        assertEquals(canonicalUserId, survivingUserId);

        // 12. Verify repository consolidation and NULL repo_id protection
        Integer survivingReposCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM repositories WHERE github_user_id = ?", Integer.class, survivingUserId);
        assertEquals(5, survivingReposCount, "Canonical user should own distinct repo and all 3 independent NULL repo_id repos");

        List<String> survivingRepoNames = jdbcTemplate.queryForList(
                "SELECT name FROM repositories WHERE github_user_id = ? ORDER BY name", String.class, survivingUserId);
        assertTrue(survivingRepoNames.contains("tasklang"));
        assertTrue(survivingRepoNames.contains("microservices-demo"));
        assertTrue(survivingRepoNames.contains("null-repo-1"));
        assertTrue(survivingRepoNames.contains("null-repo-2"));
        assertTrue(survivingRepoNames.contains("null-repo-diff-user"));

        // 13. Verify topic preservation: repo1 must contain java, spring, postgresql without duplicates
        List<String> topics = jdbcTemplate.queryForList(
                "SELECT topic FROM repository_topics WHERE repository_id = ? ORDER BY topic",
                String.class,
                repo1Id
        );
        assertEquals(List.of("java", "postgresql", "spring"), topics, "Topics from duplicate repo must be preserved without duplicates");

        // 14. Verify child table repointing to canonical repository
        Integer repo1CommitsCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM commits WHERE repository_id = ?",
                Integer.class,
                repo1Id
        );
        assertEquals(2, repo1CommitsCount, "All commits from duplicate repo must be repointed to canonical repo");

        Integer repo1IssuesCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM issues WHERE repository_id = ?",
                Integer.class,
                repo1Id
        );
        assertEquals(2, repo1IssuesCount, "All issues from duplicate repo must be repointed to canonical repo");

        Integer repo1PrsCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pull_requests WHERE repository_id = ?",
                Integer.class,
                repo1Id
        );
        assertEquals(2, repo1PrsCount, "All pull requests from duplicate repo must be repointed to canonical repo");

        Integer repo1LangCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_statistics WHERE repository_id = ?",
                Integer.class,
                repo1Id
        );
        assertEquals(2, repo1LangCount, "All language statistics from duplicate repo must be repointed to canonical repo");

        // 15. Verify user analytics consolidation: latest analytics survived and points to canonical user
        Integer analyticsCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_analytics WHERE github_user_id = ?", Integer.class, survivingUserId);
        assertEquals(1, analyticsCount, "Exactly 1 user analytics row should be attached to canonical user");
        Integer latestStars = jdbcTemplate.queryForObject(
                "SELECT total_stars FROM user_analytics WHERE github_user_id = ?", Integer.class, survivingUserId);
        assertEquals(15, latestStars, "Latest analytics values should be preserved");

        // 16. Verify developer_scores is intact and still references users(id), not touched by github_users mapping
        Integer devScoreCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM developer_scores WHERE user_id = ?",
                Integer.class,
                localUserId[0]
        );
        assertEquals(1, devScoreCount, "developer_scores must maintain its valid relationship to users table");

        // 17. Execute the actual Flyway V4 migration SQL file (adds github_created_at, composite sha index)
        ResourceDatabasePopulator populatorV4 = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V4__commit_repository_scoped_identity_and_github_created_at.sql")
        );
        populatorV4.execute(dataSource);

        // Verify entity repositories can now read the migrated records via JPA
        List<GitHubUser> jpaUsers = gitHubUserRepository.findAll();
        assertEquals(1, jpaUsers.size());
        assertNull(jpaUsers.get(0).getGithubCreatedAt(), "Legacy user githubCreatedAt should remain null");

        // 18. Verify no orphaned records across the entire database
        Integer orphanedRepos = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM repositories WHERE github_user_id NOT IN (SELECT id FROM github_users)", Integer.class);
        assertEquals(0, orphanedRepos, "No orphaned repositories should exist");

        Integer orphanedCommits = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM commits WHERE repository_id NOT IN (SELECT id FROM repositories)", Integer.class);
        assertEquals(0, orphanedCommits, "No orphaned commits should exist");

        Integer orphanedIssues = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM issues WHERE repository_id NOT IN (SELECT id FROM repositories)", Integer.class);
        assertEquals(0, orphanedIssues, "No orphaned issues should exist");

        Integer orphanedPrs = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pull_requests WHERE repository_id NOT IN (SELECT id FROM repositories)", Integer.class);
        assertEquals(0, orphanedPrs, "No orphaned PRs should exist");

        Integer orphanedLang = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_statistics WHERE repository_id NOT IN (SELECT id FROM repositories)", Integer.class);
        assertEquals(0, orphanedLang, "No orphaned language statistics should exist");

        Integer orphanedTopics = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM repository_topics WHERE repository_id NOT IN (SELECT id FROM repositories)", Integer.class);
        assertEquals(0, orphanedTopics, "No orphaned topics should exist");

        Integer orphanedAnalytics = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_analytics WHERE github_user_id NOT IN (SELECT id FROM github_users)", Integer.class);
        assertEquals(0, orphanedAnalytics, "No orphaned user analytics should exist");

        Integer orphanedDevScores = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM developer_scores WHERE user_id NOT IN (SELECT id FROM users)", Integer.class);
        assertEquals(0, orphanedDevScores, "No orphaned developer scores should exist");

        // 19. Verify V4 commit repository-scoped uniqueness
        assertDoesNotThrow(() -> {
            jdbcTemplate.update(
                    "INSERT INTO commits (sha, message, repository_id) VALUES (?, ?, ?)",
                    "commit-sha-101",
                    "Same SHA in different repo",
                    repo2Id
            );
        }, "Same SHA in different repository must be permitted");

        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO commits (sha, message, repository_id) VALUES (?, ?, ?)",
                    "commit-sha-101",
                    "Duplicate SHA in same repo",
                    repo1Id
            );
        }, "Duplicate SHA in the same repository must be rejected by unique constraint");

        // 20. Verify lowercase username uniqueness
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO github_users (username, name) VALUES (?, ?)",
                    "ashen-priyashan",
                    "Duplicate User"
            );
        }, "Case-insensitive duplicate username must be rejected");
    }

    @Test
    void shouldExecuteAllMigrationsCleanlyOnFreshDatabase() {
        jdbcTemplate.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V1__initial_schema.sql"),
                new ClassPathResource("db/migration/V2__sync_users_columns.sql"),
                new ClassPathResource("db/migration/V3__deduplicate_github_users_and_case_insensitive_index.sql"),
                new ClassPathResource("db/migration/V4__commit_repository_scoped_identity_and_github_created_at.sql")
        );
        assertDoesNotThrow(() -> populator.execute(dataSource), "All Flyway migrations must execute cleanly from scratch");

        // Verify key tables and indexes exist
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('github_users', 'repositories', 'commits', 'user_analytics', 'developer_scores')",
                Integer.class
        );
        assertEquals(5, tableCount, "All core tables must exist after full migration run");

        // Verify github_created_at column was added
        Integer colCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'github_users' AND column_name = 'github_created_at'",
                Integer.class
        );
        assertEquals(1, colCount, "github_created_at column must exist");
    }

    @Test
    void shouldPreserveExistingDataWhenMigratingExistingDatabase() {
        // Setup initial schema and seed a single user with repositories and analytics (similar to current dev DB)
        jdbcTemplate.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V1__initial_schema.sql"),
                new ClassPathResource("db/migration/V2__sync_users_columns.sql")
        ).execute(dataSource);

        jdbcTemplate.update("INSERT INTO github_users (username, name) VALUES (?, ?)", "octocat", "The Octocat");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM github_users WHERE username = 'octocat'", Long.class);
        jdbcTemplate.update("INSERT INTO repositories (repo_id, name, github_user_id) VALUES (?, ?, ?)", 9001L, "hello-world", userId);
        Long repoId = jdbcTemplate.queryForObject("SELECT id FROM repositories WHERE name = 'hello-world'", Long.class);
        jdbcTemplate.update("INSERT INTO commits (sha, message, repository_id) VALUES (?, ?, ?)", "abc123sha", "first commit", repoId);
        jdbcTemplate.update("INSERT INTO user_analytics (github_user_id, total_stars, calculated_at) VALUES (?, ?, ?)", userId, 10, LocalDateTime.now());

        // Run V3 and V4
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V3__deduplicate_github_users_and_case_insensitive_index.sql"),
                new ClassPathResource("db/migration/V4__commit_repository_scoped_identity_and_github_created_at.sql")
        );
        assertDoesNotThrow(() -> populator.execute(dataSource), "Existing valid database must migrate smoothly without error");

        // Assert all data remains intact
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM github_users", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM repositories", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM commits", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM user_analytics", Integer.class));
    }
}

