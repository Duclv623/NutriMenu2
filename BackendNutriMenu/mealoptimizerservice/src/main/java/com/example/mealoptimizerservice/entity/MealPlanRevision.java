package com.example.mealoptimizerservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Immutable before-image captured atomically with a successful regeneration. */
@Entity
@Table(name = "meal_plan_revisions", indexes = @Index(name="idx_plan_revision_owner", columnList="user_id,plan_id"))
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class MealPlanRevision {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id",nullable=false) private Long userId;
    @Column(name="plan_id",nullable=false) private Long planId;
    @Column(name="original_version",nullable=false) private Long originalVersion;
    @Column(name="snapshot_json",nullable=false,columnDefinition="text") private String snapshotJson;
    @Builder.Default @Column(name="archived_at",nullable=false) private LocalDateTime archivedAt=LocalDateTime.now();
}
