package com.gitracker.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "github_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitHubUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long githubId;

    @Column(nullable = false, unique = true)
    private String username;

    private String name;

    private String avatarUrl;

    @Column(length = 1000)
    private String bio;

    private Integer followers;

    private Integer following;

    private Integer publicRepos;
}