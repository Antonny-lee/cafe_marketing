package com.cafe.dashboard.repository;

import com.cafe.dashboard.entity.MarketReportSeries;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketReportSeriesRepository extends JpaRepository<MarketReportSeries, Long> {
    List<MarketReportSeries> findByReportIdAndMetricNameOrderById(Long reportId, String metricName);
}
