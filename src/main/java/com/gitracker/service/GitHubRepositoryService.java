package com.gitracker.service;

import com.gitracker.client.GitHubApiClient;
import com.gitracker.dto.GitHubReadmeDto;
import com.gitracker.dto.GitHubRepositoryDto;
import com.gitracker.dto.ReadmeResponse;
import com.gitracker.dto.RepositoryResponse;
import com.gitracker.entity.GitHubUser;
import com.gitracker.entity.Repository;
import com.gitracker.repository.GitHubUserRepository;
import com.gitracker.repository.RepositoryRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        Set<Long> existingRepoIds = owner == null
                ? Set.of()
                : repositoryRepository.findByOwner(owner)
                        .stream()
                        .map(Repository::getRepoId)
                        .collect(Collectors.toSet());

        return Arrays.stream(repositories)
                .map(repo -> {

                    String licenseName = repo.license() != null ? repo.license().name() : null;

                    if (owner != null && !existingRepoIds.contains(repo.id())) {

                        Repository repository = Repository.builder()
                                .repoId(repo.id())
                                .name(repo.name())
                                .description(repo.description())
                                .language(repo.language())
                                .stars(repo.stars())
                                .forks(repo.forks())
                                .watchers(repo.watchers())
                                .defaultBranch(repo.defaultBranch())
                                .openIssues(repo.openIssues())
                                .licenseName(licenseName)
                                .topics(repo.topics())
                                .htmlUrl(repo.htmlUrl())
                                .size(repo.size())
                                .archived(repo.archived())
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
                            repo.defaultBranch() != null ? repo.defaultBranch() : "main",
                            repo.openIssues(),
                            licenseName,
                            repo.topics() != null ? repo.topics() : List.of(),
                            repo.htmlUrl(),
                            repo.size(),
                            repo.archived(),
                            repo.createdAt(),
                            repo.updatedAt()
                    );
                })
                .toList();
    }

    public RepositoryResponse getRepository(String username, String repoName) {
        GitHubRepositoryDto repo = gitHubApiClient.getRepository(username, repoName);
        if (repo == null) {
            throw new com.gitracker.exception.ResourceNotFoundException(
                    "Repository '" + repoName + "' not found for owner '" + username + "'"
            );
        }

        GitHubUser owner = gitHubUserRepository.findByUsername(username).orElse(null);
        String licenseName = repo.license() != null ? repo.license().name() : null;

        if (owner != null) {
            boolean exists = repositoryRepository.findByOwner(owner).stream()
                    .anyMatch(r -> r.getRepoId().equals(repo.id()));
            if (!exists) {
                Repository repository = Repository.builder()
                        .repoId(repo.id())
                        .name(repo.name())
                        .description(repo.description())
                        .language(repo.language())
                        .stars(repo.stars())
                        .forks(repo.forks())
                        .watchers(repo.watchers())
                        .defaultBranch(repo.defaultBranch())
                        .openIssues(repo.openIssues())
                        .licenseName(licenseName)
                        .topics(repo.topics() != null ? repo.topics() : List.of())
                        .htmlUrl(repo.htmlUrl())
                        .size(repo.size())
                        .archived(repo.archived())
                        .owner(owner)
                        .build();

                repositoryRepository.save(repository);
            }
        }

        return new RepositoryResponse(
                repo.name(),
                repo.description(),
                repo.stars(),
                repo.forks(),
                repo.watchers(),
                repo.language(),
                repo.defaultBranch() != null ? repo.defaultBranch() : "main",
                repo.openIssues(),
                licenseName,
                repo.topics() != null ? repo.topics() : List.of(),
                repo.htmlUrl(),
                repo.size(),
                repo.archived(),
                repo.createdAt(),
                repo.updatedAt()
        );
    }

    public ReadmeResponse getReadme(String username, String repoName) {
        GitHubReadmeDto readmeDto = gitHubApiClient.getReadme(username, repoName);

        if (readmeDto == null || readmeDto.content() == null) {
            return new ReadmeResponse(null, null, false);
        }

        try {
            String cleanContent = readmeDto.content().replaceAll("\\s", "");
            byte[] decodedBytes = Base64.getDecoder().decode(cleanContent);
            String decodedText = new String(decodedBytes, StandardCharsets.UTF_8);
            return new ReadmeResponse(decodedText, readmeDto.htmlUrl(), true);

        } catch (IllegalArgumentException e) {
            return new ReadmeResponse(readmeDto.content(), readmeDto.htmlUrl(), true);
        }
    }
}