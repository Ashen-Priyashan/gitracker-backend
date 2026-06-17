package com.gitracker.controller;

import com.gitracker.entity.UserEntity;
import com.gitracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class UserTestController {

    private final UserService userService;

    @PostMapping("/{username}")
    public UserEntity createUser(@PathVariable String username) {
        return userService.createUser(username);
    }
}