package com.gitracker.service;

import com.gitracker.client.GitHubApiClient;
import com.gitracker.dto.GitHubUserDto;
import com.gitracker.dto.GitHubUserResponse;
import com.gitracker.entity.GitHubUser;
import com.gitracker.repository.GitHubUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class GitHubUserService {

    private static final Logger log = LoggerFactory.getLogger(GitHubUserService.class);

    private final GitHubApiClient gitHubApiClient;
    private final GitHubUserRepository gitHubUserRepository;
    private final long ttlMinutes;

    @Autowired
    public GitHubUserService(
            GitHubApiClient gitHubApiClient,
            GitHubUserRepository gitHubUserRepository,
            @Value("${github.sync.ttl-minutes:15}") long ttlMinutes
    ) {
        this.gitHubApiClient = gitHubApiClient;
        this.gitHubUserRepository = gitHubUserRepository;
        this.ttlMinutes = ttlMinutes;
    }

    public GitHubUserService(
            GitHubApiClient gitHubApiClient,
            GitHubUserRepository gitHubUserRepository
    ) {
        this(gitHubApiClient, gitHubUserRepository, 15L);
    }

    @Cacheable(value = "users", key = "#username")
    public GitHubUserResponse getUser(String username) {

        var existingUser =
                gitHubUserRepository.findByUsername(username);

        if (existingUser.isPresent()) {

            GitHubUser user = existingUser.get();
            LocalDateTime lastUpdated = user.getUpdatedAt() != null ? user.getUpdatedAt() : user.getCreatedAt();
            boolean isFresh = lastUpdated != null && lastUpdated.isAfter(LocalDateTime.now().minusMinutes(ttlMinutes));

            if (isFresh) {
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

            try {
                GitHubUserDto freshUser = gitHubApiClient.getUser(username);
                user.setName(freshUser.name());
                user.setAvatarUrl(freshUser.avatarUrl());
                user.setBio(freshUser.bio());
                user.setFollowers(freshUser.followers());
                user.setFollowing(freshUser.following());
                user.setPublicRepos(freshUser.publicRepos());
                user.setUpdatedAt(LocalDateTime.now());
                gitHubUserRepository.save(user);

                return new GitHubUserResponse(
                        freshUser.login(),
                        freshUser.name(),
                        freshUser.bio(),
                        freshUser.location(),
                        freshUser.followers(),
                        freshUser.following(),
                        freshUser.avatarUrl(),
                        freshUser.createdAt()
                );
            } catch (Exception e) {
                log.warn("Failed to refresh user {} from GitHub API, falling back to cached data: {}", username, e.getMessage());
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