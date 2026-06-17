package com.gitracker.repository;

import com.gitracker.entity.UserAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAnalyticsRepository
        extends JpaRepository<UserAnalytics, Long> {
}