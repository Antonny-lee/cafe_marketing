package com.cafe.dashboard.repository;

import com.cafe.dashboard.entity.MarketReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketReportRepository extends JpaRepository<MarketReport, Long> {
    Optional<MarketReport> findFirstByOrderByReportIdDesc();
}
