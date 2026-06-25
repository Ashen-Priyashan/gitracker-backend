package com.gitracker.repository;

import com.gitracker.entity.GitHubUser;
import com.gitracker.entity.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RepositoryRepository
        extends JpaRepository<Repository, Long> {

    Optional<Repository> findByRepoId(Long repoId);

    List<Repository> findByOwner(GitHubUser owner);
}