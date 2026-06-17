package com.gitracker.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_analytics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer totalRepositories;

    private Integer totalStars;

    private Integer totalForks;

    private String mostUsedLanguage;

    private LocalDateTime calculatedAt;

    @OneToOne
    @JoinColumn(name = "github_user_id")
    private GitHubUser githubUser;
}