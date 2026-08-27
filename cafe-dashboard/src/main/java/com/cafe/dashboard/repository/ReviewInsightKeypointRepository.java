package com.cafe.dashboard.repository;

import com.cafe.dashboard.entity.ReviewInsightKeypoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewInsightKeypointRepository extends JpaRepository<ReviewInsightKeypoint, Long> {
    List<ReviewInsightKeypoint> findByStoreIdOrderById(String storeId);
    void deleteByStoreId(String storeId);
}
