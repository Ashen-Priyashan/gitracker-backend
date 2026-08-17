package com.gitracker.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "language_statistics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LanguageStatistic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String languageName;

    private Long byteCount;

    private Double percentage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private Repository repository;
}
