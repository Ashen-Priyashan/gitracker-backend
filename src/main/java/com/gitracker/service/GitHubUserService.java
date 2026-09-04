package com.gitracker.service;

import com.gitracker.client.GitHubApiClient;
import com.gitracker.dto.GitHubUserDto;
import com.gitracker.dto.GitHubUserResponse;
import com.gitracker.entity.GitHubUser;
import com.gitracker.repository.GitHubUserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class GitHubUserService {

    private final GitHubApiClient gitHubApiClient;
    private final GitHubUserRepository gitHubUserRepository;

    public GitHubUserService(
            GitHubApiClient gitHubApiClient,
            GitHubUserRepository gitHubUserRepository
    ) {
        this.gitHubApiClient = gitHubApiClient;
        this.gitHubUserRepository = gitHubUserRepository;
    }

    @Cacheable(value = "users", key = "#username")
    public GitHubUserResponse getUser(String username) {

        var existingUser =
                gitHubUserRepository.findByUsername(username);

        if (existingUser.isPresent()) {

            GitHubUser user = existingUser.get();

            return new GitHubUserResponse(
                    user.getUsername(),
                    user.getName(),
                    user.getBio(),
                    null,
                    user.getFollowers(),
                    user.getFollowing(),
                    user.getAvatarUrl(),
                    user.getCreatedAt() != null ? user.getCreatedAt().toString() : null
            );
        }

        GitHubUserDto user = gitHubApiClient.getUser(username);

        GitHubUser githubUser = GitHubUser.builder()
                .githubId(user.id())
                .username(user.login())
                .name(user.name())
                .avatarUrl(user.avatarUrl())
                .bio(user.bio())
                .followers(user.followers())
                .following(user.following())
                .publicRepos(user.publicRepos())
                .build();

        gitHubUserRepository.save(githubUser);

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