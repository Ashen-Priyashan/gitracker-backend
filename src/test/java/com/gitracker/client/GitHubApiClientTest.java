package com.gitracker.client;

import com.gitracker.dto.GitHubRepositoryDto;
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
}
