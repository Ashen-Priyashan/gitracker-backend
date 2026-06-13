package com.gitracker.controller;

import com.gitracker.dto.GitHubUserResponse;
import com.gitracker.service.GitHubUserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final GitHubUserService gitHubUserService;

    public UserController(GitHubUserService gitHubUserService) {
        this.gitHubUserService = gitHubUserService;
    }

    @GetMapping("/{username}")
    public GitHubUserResponse getUser(
            @PathVariable String username
    ) {
        return gitHubUserService.getUser(username);
    }
}