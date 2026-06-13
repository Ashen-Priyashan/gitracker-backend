package com.gitracker.service;

import com.gitracker.client.GitHubApiClient;
import org.springframework.stereotype.Service;

@Service
public class GitHubUserService {

    private final GitHubApiClient gitHubApiClient;

    public GitHubUserService(GitHubApiClient gitHubApiClient) {
        this.gitHubApiClient = gitHubApiClient;
    }

    public String getUser(String username) {
        return gitHubApiClient.getUser(username);
    }
}