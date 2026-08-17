package com.gitracker.repository;

import com.gitracker.entity.DeveloperScore;
import com.gitracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeveloperScoreRepository extends JpaRepository<DeveloperScore, Long> {

    Optional<DeveloperScore> findByUser(UserEntity user);
}
