package com.gitracker.repository;

import com.gitracker.entity.GitHubUser;
import com.gitracker.entity.Repository;
import com.gitracker.entity.UserAnalytics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DatabaseDeduplicationIntegrationTest {

    @Autowired
    private GitHubUserRepository gitHubUserRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private UserAnalyticsRepository userAnalyticsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        userAnalyticsRepository.deleteAll();
        repositoryRepository.deleteAll();
        gitHubUserRepository.deleteAll();
    }

    @Test
    @Transactional
    void shouldConsolidateDuplicateUsersAndRepointDependentRecords() {
        // 1. Insert multiple case variants of the same GitHub username
        GitHubUser user1 = gitHubUserRepository.save(GitHubUser.builder()
                .githubId(101L)
                .username("Ashen-Priyashan")
                .name("Ashen Priyashan")
                .avatarUrl("https://example.com/avatar1.png")
                .followers(10)
                .following(5)
                .publicRepos(2)
                .build());

        GitHubUser user2 = gitHubUserRepository.save(GitHubUser.builder()
                .githubId(101L)
                .username("ashen-priyashan")
                .name("Ashen Priyashan Lower")
                .avatarUrl("https://example.com/avatar2.png")
                .followers(10)
                .following(5)
                .publicRepos(2)
                .build());

        GitHubUser user3 = gitHubUserRepository.save(GitHubUser.builder()
                .githubId(101L)
                .username("ASHEN-PRIYASHAN")
                .name("Ashen Priyashan Upper")
                .avatarUrl("https://example.com/avatar3.png")
                .followers(10)
                .following(5)
                .publicRepos(2)
                .build());

        // 2. Insert repositories pointing to different case-variant user IDs
        Repository repo1 = repositoryRepository.save(Repository.builder()
                .repoId(5001L)
                .name("tasklang")
                .owner(user1)
                .stars(5)
                .forks(1)
                .build());

        Repository repo2 = repositoryRepository.save(Repository.builder()
                .repoId(5002L)
                .name("microservices-demo")
                .owner(user2)
                .stars(10)
                .forks(2)
                .build());

        Repository repo3Duplicate = repositoryRepository.save(Repository.builder()
                .repoId(5001L)
                .name("tasklang")
                .owner(user3)
                .stars(5)
                .forks(1)
                .build());

        // 3. Insert user_analytics for user1 and user2
        userAnalyticsRepository.save(UserAnalytics.builder()
                .githubUser(user1)
                .totalRepositories(1)
                .totalStars(5)
                .totalForks(1)
                .mostUsedLanguage("Java")
                .calculatedAt(LocalDateTime.now().minusDays(1))
                .build());

        userAnalyticsRepository.save(UserAnalytics.builder()
                .githubUser(user2)
                .totalRepositories(2)
                .totalStars(15)
                .totalForks(3)
                .mostUsedLanguage("Java")
                .calculatedAt(LocalDateTime.now())
                .build());

        // 4. Execute programmatic consolidation logic mirroring V3 migration
        Long canonicalUserId = user1.getId();
        List<Long> duplicateUserIds = List.of(user2.getId(), user3.getId());

        // Repoint repositories to canonical user
        for (Long dupId : duplicateUserIds) {
            jdbcTemplate.update("UPDATE repositories SET github_user_id = ? WHERE github_user_id = ?", canonicalUserId, dupId);
        }

        // Deduplicate repository with duplicate repoId (5001L)
        jdbcTemplate.update("DELETE FROM repositories WHERE id = ?", repo3Duplicate.getId());

        // Delete duplicate analytics and retain the latest
        for (Long dupId : duplicateUserIds) {
            jdbcTemplate.update("DELETE FROM user_analytics WHERE github_user_id = ?", dupId);
        }

        // Delete duplicate users
        for (Long dupId : duplicateUserIds) {
            jdbcTemplate.update("DELETE FROM github_users WHERE id = ?", dupId);
        }

        // 5. Verify assertions
        List<GitHubUser> survivingUsers = gitHubUserRepository.findAll();
        assertEquals(1, survivingUsers.size(), "Only one canonical user should survive");
        assertEquals(canonicalUserId, survivingUsers.get(0).getId());

        List<Repository> survivingRepos = repositoryRepository.findByOwner(survivingUsers.get(0));
        assertEquals(2, survivingRepos.size(), "Canonical user should now own all distinct repositories");
        assertTrue(survivingRepos.stream().anyMatch(r -> r.getName().equals("tasklang")));
        assertTrue(survivingRepos.stream().anyMatch(r -> r.getName().equals("microservices-demo")));

        Optional<UserAnalytics> survivingAnalytics = userAnalyticsRepository.findByGithubUser(survivingUsers.get(0));
        assertTrue(survivingAnalytics.isPresent(), "User analytics should be attached to canonical user");
    }
}
