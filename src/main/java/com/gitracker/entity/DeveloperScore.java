package com.gitracker.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "developer_scores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeveloperScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer totalScore;

    private Integer activityScore;

    private Integer consistencyScore;

    private Integer collaborationScore;

    private Integer qualityScore;

    private Integer diversityScore;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", unique = true)
    private UserEntity user;

    private LocalDateTime calculatedAt;

    @PrePersist
    @PreUpdate
    public void onSave() {
        calculatedAt = LocalDateTime.now();
    }
}
