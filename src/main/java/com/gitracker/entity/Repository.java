package com.gitracker.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "repositories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Repository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long repoId;

    private String name;

    @Column(length = 1000)
    private String description;

    private String language;

    private Integer stars;

    private Integer forks;

    private Integer watchers;

    private String defaultBranch;

    private Integer openIssues;

    private String licenseName;

    @ElementCollection
    @CollectionTable(name = "repository_topics", joinColumns = @JoinColumn(name = "repository_id"))
    @Column(name = "topic")
    private List<String> topics;

    private String htmlUrl;

    private Integer size;

    private Boolean archived;

    @ManyToOne
    @JoinColumn(name = "github_user_id")
    private GitHubUser owner;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}