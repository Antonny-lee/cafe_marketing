package com.cafe.dashboard.repository;

import com.cafe.dashboard.entity.FixedCost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FixedCostRepository extends JpaRepository<FixedCost, Long> {
    List<FixedCost> findByStoreIdAndActiveOrderByDayOfMonth(String storeId, String active);
}
