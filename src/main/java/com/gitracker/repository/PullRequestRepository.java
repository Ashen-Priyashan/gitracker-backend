package com.gitracker.repository;

import com.gitracker.entity.PullRequest;
import com.gitracker.entity.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {

    List<PullRequest> findByRepository(Repository repository);

    List<PullRequest> findByRepositoryIn(List<Repository> repositories);
}
