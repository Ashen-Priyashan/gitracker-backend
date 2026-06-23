package com.gitracker.repository;

import com.gitracker.entity.GitHubUser;
import com.gitracker.entity.UserAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAnalyticsRepository
        extends JpaRepository<UserAnalytics, Long> {

    Optional<UserAnalytics> findByGithubUser(
            GitHubUser githubUser
    );
}