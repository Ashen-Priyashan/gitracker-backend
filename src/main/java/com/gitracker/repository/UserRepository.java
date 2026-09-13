package com.gitracker.repository;

import com.gitracker.entity.GitHubAppConnection;
import com.gitracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<UserEntity> findByGithubConnection(GitHubAppConnection githubConnection);

    Optional<UserEntity> findByGithubConnection_GithubId(Long githubId);

    Optional<UserEntity> findByGithubConnection_Id(Long connectionId);
}