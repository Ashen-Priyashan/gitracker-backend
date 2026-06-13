package com.gitracker.controller;

import com.gitracker.dto.RepositoryResponse;
import com.gitracker.service.GitHubRepositoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class RepositoryController {

    private final GitHubRepositoryService repositoryService;

    public RepositoryController(
            GitHubRepositoryService repositoryService
    ) {
        this.repositoryService = repositoryService;
    }

    @GetMapping("/{username}/repositories")
    public List<RepositoryResponse> getRepositories(
            @PathVariable String username
    ) {

        return repositoryService.getRepositories(username);
    }
}