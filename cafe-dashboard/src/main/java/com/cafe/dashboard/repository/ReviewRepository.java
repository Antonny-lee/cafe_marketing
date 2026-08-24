package com.cafe.dashboard.repository;

import com.cafe.dashboard.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, String> {

    Page<Review> findByStoreIdOrderByReviewDateDesc(String storeId, Pageable pageable);

    List<Review> findByStoreId(String storeId);

    long countByStoreId(String storeId);

    @Query("SELECT r.storeId AS storeId, AVG(r.rating) AS avgRating, COUNT(r) AS reviewCount " +
           "FROM Review r WHERE r.rating IS NOT NULL GROUP BY r.storeId")
    List<StoreRatingStat> aggregateRatingsByStore();

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.rating IS NOT NULL")
    Double averageRatingOverall();
}
