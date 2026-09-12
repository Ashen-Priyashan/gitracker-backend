package com.gitracker.service;

import com.gitracker.client.GitHubApiClient;
import com.gitracker.dto.*;
import com.gitracker.entity.GitHubUser;
import com.gitracker.entity.Repository;
import com.gitracker.repository.GitHubUserRepository;
import com.gitracker.repository.RepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GitHubRepositoryService {

    private static final Logger log = LoggerFactory.getLogger(GitHubRepositoryService.class);

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
        GitHubUser owner = gitHubUserRepository
                .findByUsernameIgnoreCase(username)
                .orElse(null);

        GitHubRepositoryDto[] repositories;
        try {
            repositories = gitHubApiClient.getRepositories(username);
        } catch (Exception exception) {
            if (owner != null) {
                List<Repository> cached = repositoryRepository.findByOwner(owner);
                if (!cached.isEmpty()) {
                    log.warn("Failed to fetch fresh repositories for user {} from GitHub API ({}), falling back to cached stale data ({} repos)",
                            username, exception.getMessage(), cached.size());
                    return cached.stream()
                            .map(repo -> new RepositoryResponse(
                                    repo.getName(),
                                    repo.getDescription(),
                                    repo.getStars() != null ? repo.getStars() : 0,
                                    repo.getForks() != null ? repo.getForks() : 0,
                                    repo.getWatchers() != null ? repo.getWatchers() : 0,
                                    repo.getLanguage(),
                                    repo.getDefaultBranch() != null ? repo.getDefaultBranch() : "main",
                                    repo.getOpenIssues() != null ? repo.getOpenIssues() : 0,
                                    repo.getLicenseName(),
                                    repo.getTopics() != null ? repo.getTopics() : List.of(),
                                    repo.getHtmlUrl(),
                                    repo.getSize() != null ? repo.getSize() : 0,
                                    repo.getArchived() != null ? repo.getArchived() : false,
                                    repo.getCreatedAt() != null ? repo.getCreatedAt().toString() : null,
                                    repo.getUpdatedAt() != null ? repo.getUpdatedAt().toString() : null
                            ))
                            .toList();
                }
            }
            throw exception;
        }

        Map<Long, Repository> existingRepoMap = owner == null
                ? Map.of()
                : repositoryRepository.findByOwner(owner)
                        .stream()
                        .collect(Collectors.toMap(Repository::getRepoId, r -> r, (r1, r2) -> r1));

        if (owner != null) {
            owner.setUpdatedAt(LocalDateTime.now());
            gitHubUserRepository.save(owner);
        }

        return Arrays.stream(repositories)
                .map(repo -> {

                    String licenseName = repo.license() != null ? repo.license().name() : null;

                    if (owner != null) {
                        Repository repository = existingRepoMap.get(repo.id());
                        if (repository == null) {
                            repository = Repository.builder()
                                    .repoId(repo.id())
                                    .owner(owner)
                                    .build();
                        }
                        repository.setName(repo.name());
                        repository.setDescription(repo.description());
                        repository.setLanguage(repo.language());
                        repository.setStars(repo.stars());
                        repository.setForks(repo.forks());
                        repository.setWatchers(repo.watchers());
                        repository.setDefaultBranch(repo.defaultBranch());
                        repository.setOpenIssues(repo.openIssues());
                        repository.setLicenseName(licenseName);
                        repository.setTopics(repo.topics());
                        repository.setHtmlUrl(repo.htmlUrl());
                        repository.setSize(repo.size());
                        repository.setArchived(repo.archived());

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

        GitHubUser owner = gitHubUserRepository.findByUsernameIgnoreCase(username).orElse(null);
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
            return new ReadmeResponse(null, readmeDto.htmlUrl(), false);
        }
    }

    public List<BranchResponse> getBranches(String username, String repoName) {
        GitHubBranchDto[] branches = gitHubApiClient.getBranches(username, repoName);
        if (branches == null) {
            return List.of();
        }

        return Arrays.stream(branches)
                .map(b -> new BranchResponse(
                        b.name(),
                        b.isProtected(),
                        b.commit() != null ? b.commit().sha() : null
                ))
                .toList();
    }

    public List<CommitSummaryResponse> getCommits(String username, String repoName, String branch, int page, int perPage) {
        GitHubCommitListDto[] commits = gitHubApiClient.getCommits(username, repoName, branch, page, perPage);
        if (commits == null) {
            return List.of();
        }

        return Arrays.stream(commits)
                .map(this::mapToCommitSummary)
                .toList();
    }

    public CommitDetailResponse getCommit(String username, String repoName, String sha) {
        GitHubCommitDetailDto commit = gitHubApiClient.getCommit(username, repoName, sha);
        if (commit == null) {
            throw new com.gitracker.exception.ResourceNotFoundException(
                    "Commit '" + sha + "' not found in repository '" + repoName + "' for owner '" + username + "'"
            );
        }

        return mapToCommitDetail(username, repoName, commit);
    }

    public List<ContributorResponse> getContributors(String username, String repoName) {
        GitHubContributorDto[] contributors = gitHubApiClient.getContributors(username, repoName);
        if (contributors == null) {
            return List.of();
        }

        return Arrays.stream(contributors)
                .map(c -> new ContributorResponse(
                        c.login(),
                        c.avatarUrl(),
                        c.htmlUrl(),
                        c.contributions(),
                        c.type()
                ))
                .toList();
    }

    public CommitActivityResponse getCommitActivity(String username, String repoName) {
        GitHubCommitActivityDto[] activity = gitHubApiClient.getCommitActivity(username, repoName);
        if (activity == null) {
            return new CommitActivityResponse("CALCULATING", 0, List.of());
        }

        List<CommitActivityWeekResponse> weeks = Arrays.stream(activity)
                .map(w -> new CommitActivityWeekResponse(
                        w.week(),
                        w.total(),
                        w.days() != null ? w.days() : List.of()
                ))
                .toList();
        int totalYearCommits = weeks.stream().mapToInt(CommitActivityWeekResponse::totalCommits).sum();
        return new CommitActivityResponse("READY", totalYearCommits, weeks);
    }

    public CodeFrequencyResponse getCodeFrequency(String username, String repoName) {
        Number[][] rawStats = gitHubApiClient.getCodeFrequency(username, repoName);
        if (rawStats == null) {
            return new CodeFrequencyResponse("CALCULATING", List.of());
        }

        List<CodeFrequencyWeekResponse> weeklyStats = new java.util.ArrayList<>();
        for (Number[] entry : rawStats) {
            if (entry != null && entry.length >= 3) {
                long timestamp = entry[0] != null ? entry[0].longValue() : 0L;
                int additions = entry[1] != null ? entry[1].intValue() : 0;
                int deletions = entry[2] != null ? entry[2].intValue() : 0;
                int netChange = additions + deletions;
                weeklyStats.add(new CodeFrequencyWeekResponse(timestamp, additions, deletions, netChange));
            }
        }
        return new CodeFrequencyResponse("READY", weeklyStats);
    }

    public ParticipationResponse getParticipation(String username, String repoName) {
        GitHubParticipationDto participation = gitHubApiClient.getParticipation(username, repoName);
        if (participation == null) {
            return new ParticipationResponse("CALCULATING", List.of(), List.of());
        }

        List<Integer> all = participation.all() != null ? participation.all() : List.of();
        List<Integer> owner = participation.owner() != null ? participation.owner() : List.of();
        return new ParticipationResponse("READY", all, owner);
    }

    private CommitSummaryResponse mapToCommitSummary(GitHubCommitListDto dto) {
        String sha = dto.sha();
        String shortSha = sha != null && sha.length() >= 7 ? sha.substring(0, 7) : sha;
        String message = dto.commit() != null ? dto.commit().message() : null;

        String authorName = resolveAuthorName(dto);
        String authorLogin = dto.author() != null ? dto.author().login() : null;
        String authorAvatarUrl = dto.author() != null ? dto.author().avatarUrl() : null;
        String authorHtmlUrl = dto.author() != null && dto.author().login() != null
                ? "https://github.com/" + dto.author().login()
                : (dto.htmlUrl() != null ? dto.htmlUrl() : null);

        String committerName = resolveCommitterName(dto);
        String committerLogin = dto.committer() != null ? dto.committer().login() : null;
        String committerAvatarUrl = dto.committer() != null ? dto.committer().avatarUrl() : null;
        String committerHtmlUrl = dto.committer() != null && dto.committer().login() != null
                ? "https://github.com/" + dto.committer().login()
                : null;

        String authoredAt = dto.commit() != null && dto.commit().author() != null
                ? dto.commit().author().date()
                : null;
        String committedAt = dto.commit() != null && dto.commit().committer() != null
                ? dto.commit().committer().date()
                : null;

        return new CommitSummaryResponse(
                sha,
                shortSha,
                message,
                authorName,
                authorLogin,
                authorAvatarUrl,
                authorHtmlUrl,
                committerName,
                committerLogin,
                committerAvatarUrl,
                committerHtmlUrl,
                authoredAt,
                committedAt,
                dto.htmlUrl()
        );
    }

    private CommitDetailResponse mapToCommitDetail(String username, String repoName, GitHubCommitDetailDto dto) {
        String sha = dto.sha();
        String shortSha = sha != null && sha.length() >= 7 ? sha.substring(0, 7) : sha;
        String message = dto.commit() != null ? dto.commit().message() : null;

        String authorName = resolveDetailAuthorName(dto);
        String authorLogin = dto.author() != null ? dto.author().login() : null;
        String authorAvatarUrl = dto.author() != null ? dto.author().avatarUrl() : null;
        String authorHtmlUrl = dto.author() != null && dto.author().login() != null
                ? "https://github.com/" + dto.author().login()
                : (dto.htmlUrl() != null ? dto.htmlUrl() : null);

        String committerName = resolveDetailCommitterName(dto);
        String committerLogin = dto.committer() != null ? dto.committer().login() : null;
        String committerAvatarUrl = dto.committer() != null ? dto.committer().avatarUrl() : null;
        String committerHtmlUrl = dto.committer() != null && dto.committer().login() != null
                ? "https://github.com/" + dto.committer().login()
                : null;

        String authoredAt = dto.commit() != null && dto.commit().author() != null
                ? dto.commit().author().date()
                : null;
        String committedAt = dto.commit() != null && dto.commit().committer() != null
                ? dto.commit().committer().date()
                : null;

        int additions = dto.stats() != null ? dto.stats().additions() : 0;
        int deletions = dto.stats() != null ? dto.stats().deletions() : 0;
        int totalChanges = dto.stats() != null ? dto.stats().total() : 0;

        List<CommitParentResponse> parents = dto.parents() != null
                ? dto.parents().stream()
                .map(p -> new CommitParentResponse(
                        p.sha(),
                        p.htmlUrl() != null ? p.htmlUrl() : (p.sha() != null ? "https://github.com/" + username + "/" + repoName + "/commit/" + p.sha() : null)
                ))
                .toList()
                : List.of();

        List<CommitFileResponse> files = dto.files() != null
                ? dto.files().stream()
                .map(f -> new CommitFileResponse(
                        f.filename(),
                        f.status(),
                        f.additions(),
                        f.deletions(),
                        f.changes(),
                        f.blobUrl(),
                        f.rawUrl(),
                        f.patch(),
                        f.previousFilename()
                ))
                .toList()
                : List.of();

        int changedFilesCount = files.size();

        return new CommitDetailResponse(
                sha,
                shortSha,
                message,
                authorName,
                authorLogin,
                authorAvatarUrl,
                authorHtmlUrl,
                committerName,
                committerLogin,
                committerAvatarUrl,
                committerHtmlUrl,
                authoredAt,
                committedAt,
                dto.htmlUrl(),
                additions,
                deletions,
                totalChanges,
                changedFilesCount,
                parents,
                files
        );
    }

    private String resolveAuthorName(GitHubCommitListDto dto) {
        if (dto.commit() != null && dto.commit().author() != null && dto.commit().author().name() != null && !dto.commit().author().name().isBlank()) {
            return dto.commit().author().name();
        }
        if (dto.author() != null && dto.author().login() != null) {
            return dto.author().login();
        }
        return "Unknown";
    }

    private String resolveCommitterName(GitHubCommitListDto dto) {
        if (dto.commit() != null && dto.commit().committer() != null && dto.commit().committer().name() != null && !dto.commit().committer().name().isBlank()) {
            return dto.commit().committer().name();
        }
        if (dto.committer() != null && dto.committer().login() != null) {
            return dto.committer().login();
        }
        return null;
    }

    private String resolveDetailAuthorName(GitHubCommitDetailDto dto) {
        if (dto.commit() != null && dto.commit().author() != null && dto.commit().author().name() != null && !dto.commit().author().name().isBlank()) {
            return dto.commit().author().name();
        }
        if (dto.author() != null && dto.author().login() != null) {
            return dto.author().login();
        }
        return "Unknown";
    }

    private String resolveDetailCommitterName(GitHubCommitDetailDto dto) {
        if (dto.commit() != null && dto.commit().committer() != null && dto.commit().committer().name() != null && !dto.commit().committer().name().isBlank()) {
            return dto.commit().committer().name();
        }
        if (dto.committer() != null && dto.committer().login() != null) {
            return dto.committer().login();
        }
        return null;
    }
}