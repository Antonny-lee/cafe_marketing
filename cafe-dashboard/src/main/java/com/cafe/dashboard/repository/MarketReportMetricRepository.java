package com.cafe.dashboard.repository;

import com.cafe.dashboard.entity.MarketReportMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketReportMetricRepository extends JpaRepository<MarketReportMetric, Long> {
    List<MarketReportMetric> findByReportIdOrderById(Long reportId);
}
