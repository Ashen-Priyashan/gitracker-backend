package com.gitracker.service;

import com.gitracker.entity.GitHubUser;
import com.gitracker.entity.Repository;
import com.gitracker.entity.UserAnalytics;
import com.gitracker.exception.ResourceNotFoundException;
import com.gitracker.repository.GitHubUserRepository;
import com.gitracker.repository.RepositoryRepository;
import com.gitracker.repository.UserAnalyticsRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserAnalyticsService {

    private final GitHubUserRepository gitHubUserRepository;
    private final RepositoryRepository repositoryRepository;
    private final UserAnalyticsRepository userAnalyticsRepository;
    private final GitHubUserService gitHubUserService;
    private final GitHubRepositoryService gitHubRepositoryService;

    public UserAnalyticsService(
            GitHubUserRepository gitHubUserRepository,
            RepositoryRepository repositoryRepository,
            UserAnalyticsRepository userAnalyticsRepository,
            GitHubUserService gitHubUserService,
            GitHubRepositoryService gitHubRepositoryService
    ) {
        this.gitHubUserRepository = gitHubUserRepository;
        this.repositoryRepository = repositoryRepository;
        this.userAnalyticsRepository = userAnalyticsRepository;
        this.gitHubUserService = gitHubUserService;
        this.gitHubRepositoryService = gitHubRepositoryService;
    }

    public UserAnalytics generateAnalytics(String username) {

        GitHubUser user = gitHubUserRepository
                .findByUsername(username)
                .orElseGet(() -> {
                    gitHubUserService.getUser(username);
                    return gitHubUserRepository.findByUsername(username)
                            .orElseThrow(() ->
                                    new ResourceNotFoundException(
                                            "GitHub user not found"
                                    )
                            );
                });

        List<Repository> repositories =
                repositoryRepository.findByOwner(user);

        if (repositories.isEmpty()) {
            gitHubRepositoryService.getRepositories(username);
            repositories = repositoryRepository.findByOwner(user);
        }

        int totalRepositories = repositories.size();

        int totalStars = repositories.stream()
                .mapToInt(repo -> repo.getStars() != null ? repo.getStars() : 0)
                .sum();

        int totalForks = repositories.stream()
                .mapToInt(repo -> repo.getForks() != null ? repo.getForks() : 0)
                .sum();

        String mostUsedLanguage = repositories.stream()
                .filter(repo -> repo.getLanguage() != null)
                .collect(Collectors.groupingBy(
                        Repository::getLanguage,
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Unknown");

        UserAnalytics analytics = userAnalyticsRepository
                .findByGithubUser(user)
                .orElse(
                        UserAnalytics.builder()
                                .githubUser(user)
                                .build()
                );

        analytics.setTotalRepositories(totalRepositories);
        analytics.setTotalStars(totalStars);
        analytics.setTotalForks(totalForks);
        analytics.setMostUsedLanguage(mostUsedLanguage);
        analytics.setCalculatedAt(LocalDateTime.now());

        return userAnalyticsRepository.save(analytics);
    }
}