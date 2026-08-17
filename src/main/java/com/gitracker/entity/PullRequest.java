package com.gitracker.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pull_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PullRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long prNumber;

    @Column(length = 1024)
    private String title;

    private String state;

    private Integer additions;

    private Integer deletions;

    private LocalDateTime createdAt;

    private LocalDateTime mergedAt;

    private LocalDateTime closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private Repository repository;
}
