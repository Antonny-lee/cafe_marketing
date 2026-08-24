package com.cafe.dashboard.repository;

public interface StoreRatingStat {
    String getStoreId();
    Double getAvgRating();
    Long getReviewCount();
}
