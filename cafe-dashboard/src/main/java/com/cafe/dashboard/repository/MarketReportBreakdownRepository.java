package com.cafe.dashboard.repository;

import com.cafe.dashboard.entity.MarketReportBreakdown;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketReportBreakdownRepository extends JpaRepository<MarketReportBreakdown, Long> {
    List<MarketReportBreakdown> findByReportIdAndCategoryOrderById(Long reportId, String category);
}
