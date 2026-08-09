package com.gitracker.service;

import com.gitracker.entity.UserEntity;
import com.gitracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserEntity createUser(String username) {

        UserEntity user = UserEntity.builder()
                .githubUsername(username)
                .build();

        return userRepository.save(user);
    }
}