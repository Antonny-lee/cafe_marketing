package com.cafe.dashboard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "review_insight_keypoint")
@Getter
@Setter
public class ReviewInsightKeypoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", length = 10)
    private String storeId;

    @Column(name = "icon", length = 10)
    private String icon;

    @Column(name = "text", length = 200)
    private String text;
}
