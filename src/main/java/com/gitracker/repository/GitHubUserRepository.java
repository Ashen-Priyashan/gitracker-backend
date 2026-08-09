package com.gitracker.repository;

import com.gitracker.entity.GitHubUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GitHubUserRepository
        extends JpaRepository<GitHubUser, Long> {

    Optional<GitHubUser> findByUsername(String username);
}