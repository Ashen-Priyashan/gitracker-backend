package com.gitracker.repository;

import com.gitracker.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository
        extends JpaRepository<UserProfile, Long> {
}