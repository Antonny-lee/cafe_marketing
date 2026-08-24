package com.cafe.dashboard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "review_category_tags")
@Getter
@Setter
public class ReviewCategoryTag {

    @EmbeddedId
    private ReviewCategoryTagId id;

    @MapsId("storeId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @Column(name = "mention_count")
    private Integer mentionCount;

    @Column(name = "tag_category", length = 50)
    private String tagCategory;

    @Column(name = "store_total_participants")
    private Integer storeTotalParticipants;
}
