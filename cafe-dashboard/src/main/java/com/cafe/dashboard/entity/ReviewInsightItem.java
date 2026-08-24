package com.cafe.dashboard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "review_insight_item")
@Getter
@Setter
public class ReviewInsightItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", length = 10)
    private String storeId;

    @Column(name = "quote", length = 1000)
    private String quote;

    @Column(name = "suggestion", length = 500)
    private String suggestion;
}
