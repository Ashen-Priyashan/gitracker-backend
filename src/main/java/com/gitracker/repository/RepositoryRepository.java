package com.gitracker.repository;

import com.gitracker.entity.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositoryRepository
        extends JpaRepository<Repository, Long> {
}