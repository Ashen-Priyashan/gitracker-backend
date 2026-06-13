package com.gitracker.service;

import com.gitracker.client.GitHubApiClient;
import com.gitracker.dto.GitHubRepositoryDto;
import com.gitracker.dto.RepositoryResponse;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class GitHubRepositoryService {

    private final GitHubApiClient gitHubApiClient;

    public GitHubRepositoryService(
            GitHubApiClient gitHubApiClient
    ) {
        this.gitHubApiClient = gitHubApiClient;
    }

    public List<RepositoryResponse> getRepositories(
            String username
    ) {

        GitHubRepositoryDto[] repositories =
                gitHubApiClient.getRepositories(username);

        return Arrays.stream(repositories)
                .map(repo -> new RepositoryResponse(
                        repo.name(),
                        repo.description(),
                        repo.stars(),
                        repo.forks(),
                        repo.watchers(),
                        repo.language(),
                        repo.createdAt(),
                        repo.updatedAt()
                ))
                .toList();
    }
}