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

    public UserAnalyticsService(
            GitHubUserRepository gitHubUserRepository,
            RepositoryRepository repositoryRepository,
            UserAnalyticsRepository userAnalyticsRepository
    ) {
        this.gitHubUserRepository = gitHubUserRepository;
        this.repositoryRepository = repositoryRepository;
        this.userAnalyticsRepository = userAnalyticsRepository;
    }

    public UserAnalytics generateAnalytics(String username) {

        GitHubUser user = gitHubUserRepository
                .findByUsername(username)
                .orElseThrow(() ->
        new ResourceNotFoundException(
                "GitHub user not found"
        )
);

        List<Repository> repositories =
                repositoryRepository.findAll()
                        .stream()
                        .filter(repo ->
                                repo.getOwner().getId().equals(user.getId()))
                        .toList();

        int totalRepositories = repositories.size();

        int totalStars = repositories.stream()
                .mapToInt(Repository::getStars)
                .sum();

        int totalForks = repositories.stream()
                .mapToInt(Repository::getForks)
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