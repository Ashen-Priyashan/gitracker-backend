package com.gitracker.service;

import com.gitracker.client.GitHubApiClient;
import com.gitracker.dto.GitHubRepositoryDto;
import com.gitracker.dto.RepositoryResponse;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

import com.gitracker.entity.GitHubUser;
import com.gitracker.entity.Repository;
import com.gitracker.repository.GitHubUserRepository;
import com.gitracker.repository.RepositoryRepository;

@Service
public class GitHubRepositoryService {

    private final GitHubApiClient gitHubApiClient;
    private final RepositoryRepository repositoryRepository;
    private final GitHubUserRepository gitHubUserRepository;

public GitHubRepositoryService(
        GitHubApiClient gitHubApiClient,
        RepositoryRepository repositoryRepository,
        GitHubUserRepository gitHubUserRepository
) {
    this.gitHubApiClient = gitHubApiClient;
    this.repositoryRepository = repositoryRepository;
    this.gitHubUserRepository = gitHubUserRepository;
}

    public List<RepositoryResponse> getRepositories(
            String username
    ) {

        GitHubRepositoryDto[] repositories =
                gitHubApiClient.getRepositories(username);
        GitHubUser owner = gitHubUserRepository
                      .findByUsername(username)
                      .orElse(null);

        return Arrays.stream(repositories)
        .map(repo -> {

            if (owner != null &&
                    repositoryRepository.findByRepoId(repo.id()).isEmpty()) {

                Repository repository = Repository.builder()
                        .repoId(repo.id())
                        .name(repo.name())
                        .description(repo.description())
                        .language(repo.language())
                        .stars(repo.stars())
                        .forks(repo.forks())
                        .watchers(repo.watchers())
                        .owner(owner)
                        .build();

                repositoryRepository.save(repository);
            }

            return new RepositoryResponse(
                    repo.name(),
                    repo.description(),
                    repo.stars(),
                    repo.forks(),
                    repo.watchers(),
                    repo.language(),
                    repo.createdAt(),
                    repo.updatedAt()
            );
        })
        .toList();
    }
}