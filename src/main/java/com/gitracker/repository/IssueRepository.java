package com.gitracker.repository;

import com.gitracker.entity.Issue;
import com.gitracker.entity.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueRepository extends JpaRepository<Issue, Long> {

    List<Issue> findByRepository(Repository repository);

    List<Issue> findByRepositoryIn(List<Repository> repositories);
}
