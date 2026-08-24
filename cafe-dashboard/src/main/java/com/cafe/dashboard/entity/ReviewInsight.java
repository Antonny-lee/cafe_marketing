package com.cafe.dashboard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "review_insight")
@Getter
@Setter
public class ReviewInsight {

    @Id
    @Column(name = "store_id", length = 10)
    private String storeId;

    @Column(name = "positive_ratio")
    private Double positiveRatio;

    @Column(name = "negative_ratio")
    private Double negativeRatio;

    @Column(name = "analyzed_count")
    private Integer analyzedCount;

    @Column(name = "word_summary", length = 2000)
    private String wordSummary;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    /** Ready-to-use conic-gradient CSS for the "종합 평가" donut. */
    public String getDonutGradientCss() {
        double pos = positiveRatio == null ? 0 : positiveRatio;
        double neg = negativeRatio == null ? 0 : negativeRatio;
        double posDeg = pos * 3.6;
        double negEndDeg = (pos + neg) * 3.6;
        return "background: conic-gradient(#3E4A2A 0deg " + posDeg + "deg, #A8462E " + posDeg + "deg "
                + negEndDeg + "deg, #D8CDB6 " + negEndDeg + "deg 360deg);";
    }
}
