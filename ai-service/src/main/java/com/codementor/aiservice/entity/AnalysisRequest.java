package com.codementor.aiservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisRequest {

    @Id
    @Column(nullable = false, updatable = false)
    private String id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String sourceCode;

    @Column(columnDefinition = "TEXT")
    private String prompt;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(columnDefinition = "TEXT")
    private String aiResponse;

    @CreationTimestamp
    private LocalDateTime createdAt;
}