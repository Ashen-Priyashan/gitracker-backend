package com.gitracker.repository;

import com.gitracker.entity.GitHubAppConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GitHubAppConnectionRepository extends JpaRepository<GitHubAppConnection, Long> {

    Optional<GitHubAppConnection> findByUsername(String username);

    Optional<GitHubAppConnection> findByGithubId(Long githubId);
}
