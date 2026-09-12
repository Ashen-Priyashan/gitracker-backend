package com.gitracker.repository;

import com.gitracker.entity.Commit;
import com.gitracker.entity.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommitRepository extends JpaRepository<Commit, Long> {

    Optional<Commit> findBySha(String sha);

    Optional<Commit> findByRepositoryAndSha(Repository repository, String sha);

    Optional<Commit> findByRepositoryIdAndSha(Long repositoryId, String sha);

    List<Commit> findByRepository(Repository repository);

    List<Commit> findByRepositoryIn(List<Repository> repositories);

    boolean existsBySha(String sha);

    boolean existsByRepositoryAndSha(Repository repository, String sha);

    boolean existsByRepositoryIdAndSha(Long repositoryId, String sha);
}
