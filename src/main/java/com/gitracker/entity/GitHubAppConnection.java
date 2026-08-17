package com.gitracker.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "github_app_connections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitHubAppConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long githubId;

    @Column(nullable = false, unique = true)
    private String username;

    private String name;

    private String avatarUrl;

    @Column(length = 2048)
    private String accessToken;

    private String tokenType;

    private String scope;

    private LocalDateTime connectedAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        connectedAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
