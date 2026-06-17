package com.gitracker.service;

import com.gitracker.client.GitHubApiClient;
import com.gitracker.dto.GitHubUserDto;
import com.gitracker.dto.GitHubUserResponse;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;

@Service
public class GitHubUserService {

    private final GitHubApiClient gitHubApiClient;

    public GitHubUserService(GitHubApiClient gitHubApiClient) {
        this.gitHubApiClient = gitHubApiClient;
    }

    @Cacheable(value = "users", key = "#username")
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