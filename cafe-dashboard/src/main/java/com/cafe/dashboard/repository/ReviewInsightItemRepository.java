package com.cafe.dashboard.repository;

import com.cafe.dashboard.entity.ReviewInsightItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewInsightItemRepository extends JpaRepository<ReviewInsightItem, Long> {
    List<ReviewInsightItem> findByStoreIdOrderById(String storeId);
    void deleteByStoreId(String storeId);
}
