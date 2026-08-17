package com.gitracker.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "issues")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Issue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long issueNumber;

    @Column(length = 1024)
    private String title;

    private String state;

    private LocalDateTime createdAt;

    private LocalDateTime closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private Repository repository;
}
