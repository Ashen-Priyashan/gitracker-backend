package com.gitracker.service;

import com.gitracker.client.GitHubApiClient;
import com.gitracker.dto.GitHubUserDto;
import com.gitracker.dto.GitHubUserResponse;
import org.springframework.stereotype.Service;

@Service
public class GitHubUserService {

    private final GitHubApiClient gitHubApiClient;

    public GitHubUserService(GitHubApiClient gitHubApiClient) {
        this.gitHubApiClient = gitHubApiClient;
    }

    public GitHubUserResponse getUser(String username) {

        GitHubUserDto user = gitHubApiClient.getUser(username);

        return new GitHubUserResponse(
                user.login(),
                user.name(),
                user.bio(),
                user.location(),
                user.followers(),
                user.following(),
                user.avatarUrl(),
                user.createdAt()
        );
    }
}