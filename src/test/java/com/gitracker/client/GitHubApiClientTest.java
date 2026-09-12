package com.gitracker.client;

import com.gitracker.dto.*;
import com.gitracker.exception.RateLimitExceededException;
import com.gitracker.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubApiClientTest {

    private MockRestServiceServer server;
    private GitHubApiClient gitHubApiClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.com");
        this.server = MockRestServiceServer.bindTo(builder).build();
        this.gitHubApiClient = new GitHubApiClient(builder.build());
    }

    private String repoJson(long id, String name) {
        return String.format("""
                {
                    "id": %d,
                    "name": "%s",
                    "stargazers_count": 10,
                    "forks_count": 2,
                    "watchers_count": 10,
                    "open_issues_count": 0,
                    "size": 100,
                    "archived": false,
                    "language": "Java"
                }""", id, name);
    }

    @Test
    void shouldFetchSinglePageOfRepositories() {
        String json = "[" + repoJson(1, "repo1") + "," + repoJson(2, "repo2") + "]";

        server.expect(requestTo("https://api.github.com/users/octocat/repos?per_page=100&page=1&sort=updated"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        GitHubRepositoryDto[] repos = gitHubApiClient.getRepositories("octocat");

        assertNotNull(repos);
        assertEquals(2, repos.length);
        assertEquals("repo1", repos[0].name());
        assertEquals("repo2", repos[1].name());
        server.verify();
    }

    @Test
    void shouldFetchMultiplePagesOfRepositories() {
        StringBuilder page1Json = new StringBuilder("[");
        for (int i = 1; i <= 100; i++) {
            page1Json.append(repoJson(i, "repo" + i));
            if (i < 100) page1Json.append(",");
        }
        page1Json.append("]");

        String page2Json = "[" + repoJson(101, "repo101") + "]";

        server.expect(requestTo("https://api.github.com/users/octocat/repos?per_page=100&page=1&sort=updated"))
                .andRespond(withSuccess(page1Json.toString(), MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/users/octocat/repos?per_page=100&page=2&sort=updated"))
                .andRespond(withSuccess(page2Json, MediaType.APPLICATION_JSON));

        GitHubRepositoryDto[] repos = gitHubApiClient.getRepositories("octocat");

        assertNotNull(repos);
        assertEquals(101, repos.length);
        assertEquals("repo1", repos[0].name());
        assertEquals("repo101", repos[100].name());
        server.verify();
    }

    @Test
    void shouldHandleEmptyRepositoriesResult() {
        server.expect(requestTo("https://api.github.com/users/emptyuser/repos?per_page=100&page=1&sort=updated"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        GitHubRepositoryDto[] repos = gitHubApiClient.getRepositories("emptyuser");

        assertNotNull(repos);
        assertEquals(0, repos.length);
        server.verify();
    }

    @Test
    void shouldThrowResourceNotFoundWhenUserNotFound() {
        server.expect(requestTo("https://api.github.com/users/unknown/repos?per_page=100&page=1&sort=updated"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(ResourceNotFoundException.class, () -> gitHubApiClient.getRepositories("unknown"));
        server.verify();
    }

    @Test
    void shouldFetchBranchesSuccessfully() {
        String json = """
                [
                    {
                        "name": "main",
                        "protected": false,
                        "commit": {
                            "sha": "c5b97d5ae6c19d5c5df71a34c7fbeeda2479ccbc",
                            "url": "https://api.github.com/repos/octocat/Hello-World/commits/c5b97d5ae6c19d5c5df71a34c7fbeeda2479ccbc"
                        }
                    },
                    {
                        "name": "develop",
                        "protected": true,
                        "commit": {
                            "sha": "91bc72a6c19d5c5df71a34c7fbeeda2479ccbc11",
                            "url": "https://api.github.com/repos/octocat/Hello-World/commits/91bc72a6c19d5c5df71a34c7fbeeda2479ccbc11"
                        }
                    }
                ]""";

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/branches?per_page=100&page=1"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        GitHubBranchDto[] branches = gitHubApiClient.getBranches("octocat", "Hello-World");

        assertNotNull(branches);
        assertEquals(2, branches.length);
        assertEquals("main", branches[0].name());
        assertFalse(branches[0].isProtected());
        assertEquals("c5b97d5ae6c19d5c5df71a34c7fbeeda2479ccbc", branches[0].commit().sha());
        assertEquals("develop", branches[1].name());
        assertTrue(branches[1].isProtected());
        server.verify();
    }

    @Test
    void shouldThrowResourceNotFoundWhenFetchingBranchesForNonExistentRepo() {
        server.expect(requestTo("https://api.github.com/repos/octocat/unknown-repo/branches?per_page=100&page=1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(ResourceNotFoundException.class, () -> gitHubApiClient.getBranches("octocat", "unknown-repo"));
        server.verify();
    }

    @Test
    void shouldThrowRateLimitExceededWhenFetchingBranchesRateLimited() {
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/branches?per_page=100&page=1"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .header("X-RateLimit-Remaining", "0")
                        .header("Retry-After", "60"));

        assertThrows(RateLimitExceededException.class, () -> gitHubApiClient.getBranches("octocat", "Hello-World"));
        server.verify();
    }

    @Test
    void shouldThrowGitHubForbiddenWhenFetchingBranchesForbidden() {
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/branches?per_page=100&page=1"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Repository access blocked\"}"));

        assertThrows(com.gitracker.exception.GitHubForbiddenException.class, () -> gitHubApiClient.getBranches("octocat", "Hello-World"));
        server.verify();
    }

    @Test
    void shouldFetchCommitsForBranchWithPagination() {
        String json = """
                [
                    {
                        "sha": "6dcb09b5b57875f334f61aebed695e2e4193db5e",
                        "commit": {
                            "author": { "name": "Monalisa Octocat", "email": "octocat@github.com", "date": "2026-09-01T12:00:00Z" },
                            "committer": { "name": "Monalisa Octocat", "email": "octocat@github.com", "date": "2026-09-01T12:00:00Z" },
                            "message": "Fix authentication bug"
                        },
                        "html_url": "https://github.com/octocat/Hello-World/commit/6dcb09b5b57875f334f61aebed695e2e4193db5e",
                        "author": {
                            "login": "octocat",
                            "avatar_url": "https://avatars.githubusercontent.com/u/583231"
                        }
                    }
                ]""";

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/commits?sha=develop&page=1&per_page=20"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        GitHubCommitListDto[] commits = gitHubApiClient.getCommits("octocat", "Hello-World", "develop", 1, 20);

        assertNotNull(commits);
        assertEquals(1, commits.length);
        assertEquals("6dcb09b5b57875f334f61aebed695e2e4193db5e", commits[0].sha());
        assertEquals("Fix authentication bug", commits[0].commit().message());
        assertEquals("octocat", commits[0].author().login());
        server.verify();
    }

    @Test
    void shouldHandleEmptyRepositoryConflictOnCommits() {
        server.expect(requestTo("https://api.github.com/repos/octocat/empty-repo/commits?sha=main&page=1&per_page=20"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        GitHubCommitListDto[] commits = gitHubApiClient.getCommits("octocat", "empty-repo", "main", 1, 20);

        assertNotNull(commits);
        assertEquals(0, commits.length);
        server.verify();
    }

    @Test
    void shouldFetchCommitDetailSuccessfully() {
        String json = """
                {
                    "sha": "6dcb09b5b57875f334f61aebed695e2e4193db5e",
                    "commit": {
                        "author": { "name": "Monalisa Octocat", "email": "octocat@github.com", "date": "2026-09-01T12:00:00Z" },
                        "committer": { "name": "GitHub", "email": "noreply@github.com", "date": "2026-09-01T12:00:00Z" },
                        "message": "Fix authentication bug\\n\\nDetailed explanation."
                    },
                    "html_url": "https://github.com/octocat/Hello-World/commit/6dcb09b5b57875f334f61aebed695e2e4193db5e",
                    "author": { "login": "octocat", "avatar_url": "https://avatars.githubusercontent.com/u/583231" },
                    "committer": null,
                    "stats": { "total": 12, "additions": 10, "deletions": 2 },
                    "parents": [
                        { "sha": "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b", "html_url": "https://github.com/octocat/Hello-World/commit/1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b" }
                    ],
                    "files": [
                        {
                            "filename": "src/Auth.java",
                            "status": "modified",
                            "additions": 10,
                            "deletions": 2,
                            "changes": 12,
                            "patch": "@@ -1,3 +1,11 @@\\n+new line"
                        }
                    ]
                }""";

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/commits/6dcb09b5b57875f334f61aebed695e2e4193db5e"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        GitHubCommitDetailDto detail = gitHubApiClient.getCommit("octocat", "Hello-World", "6dcb09b5b57875f334f61aebed695e2e4193db5e");

        assertNotNull(detail);
        assertEquals("6dcb09b5b57875f334f61aebed695e2e4193db5e", detail.sha());
        assertEquals(12, detail.stats().total());
        assertEquals(10, detail.stats().additions());
        assertEquals(2, detail.stats().deletions());
        assertEquals(1, detail.files().size());
        assertEquals("src/Auth.java", detail.files().get(0).filename());
        assertEquals("modified", detail.files().get(0).status());
        assertEquals(1, detail.parents().size());
        server.verify();
    }

    @Test
    void shouldFetchContributorsSuccessfully() {
        String json = """
                [
                    {
                        "login": "octocat",
                        "avatar_url": "https://avatars.githubusercontent.com/u/583231",
                        "html_url": "https://github.com/octocat",
                        "contributions": 42,
                        "type": "User"
                    }
                ]""";

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/contributors?per_page=100"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        GitHubContributorDto[] contributors = gitHubApiClient.getContributors("octocat", "Hello-World");

        assertNotNull(contributors);
        assertEquals(1, contributors.length);
        assertEquals("octocat", contributors[0].login());
        assertEquals(42, contributors[0].contributions());
        server.verify();
    }

    @Test
    void shouldFetchCommitActivitySuccessfully() {
        String json = """
                [
                    {
                        "week": 1640000000,
                        "total": 5,
                        "days": [0, 1, 2, 0, 1, 1, 0]
                    }
                ]""";

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/stats/commit_activity"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        GitHubCommitActivityDto[] activity = gitHubApiClient.getCommitActivity("octocat", "Hello-World");

        assertNotNull(activity);
        assertEquals(1, activity.length);
        assertEquals(5, activity[0].total());
        assertEquals(1640000000L, activity[0].week());
        server.verify();
    }

    @Test
    void shouldReturnNullWhenCommitActivityIsCalculating() {
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/stats/commit_activity"))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        GitHubCommitActivityDto[] activity = gitHubApiClient.getCommitActivity("octocat", "Hello-World");

        assertNull(activity);
        server.verify();
    }

    @Test
    void shouldReturnNullWhenCommitActivityReturnsEmptyObjectOn202() {
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/stats/commit_activity"))
                .andRespond(withStatus(HttpStatus.ACCEPTED).body("{}").contentType(MediaType.APPLICATION_JSON));

        GitHubCommitActivityDto[] activity = gitHubApiClient.getCommitActivity("octocat", "Hello-World");

        assertNull(activity);
        server.verify();
    }

    @Test
    void shouldReturnNullWhenCommitActivityReturnsEmptyObjectOn200() {
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/stats/commit_activity"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        GitHubCommitActivityDto[] activity = gitHubApiClient.getCommitActivity("octocat", "Hello-World");

        assertNull(activity);
        server.verify();
    }

    @Test
    void shouldFetchCodeFrequencySuccessfully() {
        String json = """
                [
                    [1640000000, 150, -30],
                    [1640604800, 200, -50]
                ]""";

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/stats/code_frequency"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Number[][] stats = gitHubApiClient.getCodeFrequency("octocat", "Hello-World");

        assertNotNull(stats);
        assertEquals(2, stats.length);
        assertEquals(150, stats[0][1].intValue());
        assertEquals(-30, stats[0][2].intValue());
        server.verify();
    }

    @Test
    void shouldReturnNullWhenCodeFrequencyReturnsEmptyObject() {
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/stats/code_frequency"))
                .andRespond(withStatus(HttpStatus.ACCEPTED).body("{}").contentType(MediaType.APPLICATION_JSON));

        Number[][] stats = gitHubApiClient.getCodeFrequency("octocat", "Hello-World");

        assertNull(stats);
        server.verify();
    }

    @Test
    void shouldFetchParticipationSuccessfully() {
        String json = """
                {
                    "all": [1, 2, 3],
                    "owner": [1, 0, 1]
                }""";

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/stats/participation"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        GitHubParticipationDto participation = gitHubApiClient.getParticipation("octocat", "Hello-World");

        assertNotNull(participation);
        assertEquals(3, participation.all().size());
        assertEquals(3, participation.owner().size());
        server.verify();
    }

    @Test
    void shouldReturnNullWhenParticipationReturnsEmptyObject() {
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/stats/participation"))
                .andRespond(withStatus(HttpStatus.ACCEPTED).body("{}").contentType(MediaType.APPLICATION_JSON));

        GitHubParticipationDto participation = gitHubApiClient.getParticipation("octocat", "Hello-World");

        assertNull(participation);
        server.verify();
    }

    @Test
    void shouldThrowRateLimitExceededOn429TooManyRequests() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Retry-After", "60");
        headers.add("X-RateLimit-Reset", "1640000000");

        server.expect(requestTo("https://api.github.com/users/octocat"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, () -> gitHubApiClient.getUser("octocat"));
        assertEquals(60L, ex.getRetryAfterSeconds());
        assertEquals(1640000000L, ex.getResetTimeSeconds());
        server.verify();
    }

    @Test
    void shouldThrowRateLimitExceededOn403WithZeroRemainingHeader() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-RateLimit-Remaining", "0");
        headers.add("X-RateLimit-Reset", "1640000000");

        server.expect(requestTo("https://api.github.com/users/octocat"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).headers(headers));

        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, () -> gitHubApiClient.getUser("octocat"));
        assertEquals(1640000000L, ex.getResetTimeSeconds());
        server.verify();
    }

    @Test
    void shouldThrowRateLimitExceededOn403WithRateLimitBody() {
        server.expect(requestTo("https://api.github.com/users/octocat"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .body("{\"message\": \"API rate limit exceeded for user\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThrows(RateLimitExceededException.class, () -> gitHubApiClient.getUser("octocat"));
        server.verify();
    }

    @Test
    void shouldThrowGitHubForbiddenOn403WithoutRateLimit() {
        server.expect(requestTo("https://api.github.com/users/secretuser"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .body("{\"message\": \"Resource access is restricted\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThrows(com.gitracker.exception.GitHubForbiddenException.class, () -> gitHubApiClient.getUser("secretuser"));
        server.verify();
    }

    @Test
    void shouldThrowGitHubAuthenticationOn401Unauthorized() {
        server.expect(requestTo("https://api.github.com/user"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(com.gitracker.exception.GitHubAuthenticationException.class, () -> gitHubApiClient.getAuthenticatedUser("invalid-token"));
        server.verify();
    }

    @Test
    void shouldThrowGitHubValidationOn422UnprocessableEntity() {
        server.expect(requestTo("https://api.github.com/users/invalid/repos?per_page=100&page=1&sort=updated"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThrows(com.gitracker.exception.GitHubValidationException.class, () -> gitHubApiClient.getRepositories("invalid"));
        server.verify();
    }

    @Test
    void shouldThrowGitHubServiceUnavailableOn500ServerError() {
        server.expect(requestTo("https://api.github.com/users/octocat"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(com.gitracker.exception.GitHubServiceUnavailableException.class, () -> gitHubApiClient.getUser("octocat"));
        server.verify();
    }

    @Test
    void shouldThrowGitHubServiceUnavailableOn503ServiceUnavailable() {
        server.expect(requestTo("https://api.github.com/users/octocat"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(com.gitracker.exception.GitHubServiceUnavailableException.class, () -> gitHubApiClient.getUser("octocat"));
        server.verify();
    }

    @Test
    void shouldPropagateErrorInGetRepoCommitsOn500() {
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/commits?per_page=30"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(com.gitracker.exception.GitHubServiceUnavailableException.class, () ->
                gitHubApiClient.getRepoCommits("octocat", "Hello-World", "token"));
        server.verify();
    }

    @Test
    void shouldReturnEmptyListInGetRepoCommitsOn409Conflict() {
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/commits?per_page=30"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        var commits = gitHubApiClient.getRepoCommits("octocat", "Hello-World", "token");
        assertNotNull(commits);
        assertTrue(commits.isEmpty());
        server.verify();
    }

    @Test
    void shouldPropagateErrorInGetRepoPullRequestsOn401() {
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=30"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(com.gitracker.exception.GitHubAuthenticationException.class, () ->
                gitHubApiClient.getRepoPullRequests("octocat", "Hello-World", "bad-token"));
        server.verify();
    }

    @Test
    void shouldPropagateErrorInGetRepoLanguagesOn429() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Retry-After", "120");

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/languages"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, () ->
                gitHubApiClient.getRepoLanguages("octocat", "Hello-World", "token"));
        assertEquals(120L, ex.getRetryAfterSeconds());
        server.verify();
    }

    @Test
    void shouldPropagateErrorInGetContributorsOn500() {
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/contributors?per_page=100"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(com.gitracker.exception.GitHubServiceUnavailableException.class, () ->
                gitHubApiClient.getContributors("octocat", "Hello-World"));
        server.verify();
    }

    @Test
    void shouldPropagateErrorInGetReadmeOn503() {
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/readme"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(com.gitracker.exception.GitHubServiceUnavailableException.class, () ->
                gitHubApiClient.getReadme("octocat", "Hello-World"));
        server.verify();
    }
}
