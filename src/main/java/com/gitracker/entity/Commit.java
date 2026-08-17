package com.gitracker.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "commits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Commit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sha;

    @Column(length = 2048)
    private String message;

    private String authorName;

    private String authorEmail;

    private LocalDateTime committedAt;

    private Integer additions;

    private Integer deletions;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private Repository repository;
}
