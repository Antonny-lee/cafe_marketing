package com.cafe.dashboard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "review_insight_comparison")
@Getter
@Setter
public class ReviewInsightComparison {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", length = 10)
    private String storeId;

    @Column(name = "rival_store_id", length = 10)
    private String rivalStoreId;

    @Column(name = "strength", length = 2000)
    private String strength;

    @Column(name = "difference", length = 2000)
    private String difference;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;
}
