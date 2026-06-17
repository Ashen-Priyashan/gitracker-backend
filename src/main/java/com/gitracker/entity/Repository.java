package com.gitracker.entity;

import jakarta.persistence.*;
import lombok.*;

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

    @ManyToOne
    @JoinColumn(name = "github_user_id")
    private GitHubUser owner;
}