package com.cafe.dashboard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity
@Table(name = "reviews")
@Getter
@Setter
public class Review {

    @Id
    @Column(name = "review_id", length = 20)
    private String reviewId;

    @Column(name = "store_id", length = 10, insertable = false, updatable = false)
    private String storeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @Column(name = "reviewer_id", length = 50)
    private String reviewerId;

    @Column(name = "rating")
    private Double rating;

    @Column(name = "visit_time", length = 20)
    private String visitTime;

    @Column(name = "wait_time", length = 100)
    private String waitTime;

    @Column(name = "tags", length = 1000)
    private String tags;

    @Lob
    @Column(name = "review_text")
    private String reviewText;

    @Column(name = "review_date_text", length = 20)
    private String reviewDateText;

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @Column(name = "visit_count_text", length = 30)
    private String visitCountText;

    @Column(name = "visit_count")
    private Integer visitCount;

    /** "저녁 · 바로 입장 · 1번째 방문" style summary line for review cards. */
    public String getMetaLine() {
        return Stream.of(visitTime, waitTime, visitCountText)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" · "));
    }
}
