package com.gitracker.repository;

import com.gitracker.entity.LanguageStatistic;
import com.gitracker.entity.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LanguageStatisticRepository extends JpaRepository<LanguageStatistic, Long> {

    List<LanguageStatistic> findByRepository(Repository repository);

    List<LanguageStatistic> findByRepositoryIn(List<Repository> repositories);

    void deleteByRepository(Repository repository);
}
