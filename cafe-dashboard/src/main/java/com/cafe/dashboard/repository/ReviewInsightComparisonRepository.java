package com.cafe.dashboard.repository;

import com.cafe.dashboard.entity.ReviewInsightComparison;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewInsightComparisonRepository extends JpaRepository<ReviewInsightComparison, Long> {
    List<ReviewInsightComparison> findByStoreIdAndRivalStoreIdIn(String storeId, List<String> rivalStoreIds);
    Optional<ReviewInsightComparison> findByStoreIdAndRivalStoreId(String storeId, String rivalStoreId);
}
