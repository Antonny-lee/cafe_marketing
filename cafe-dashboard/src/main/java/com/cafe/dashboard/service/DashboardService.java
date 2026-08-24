package com.cafe.dashboard.service;

import com.cafe.dashboard.entity.MarketReport;
import com.cafe.dashboard.entity.MarketReportMetric;
import com.cafe.dashboard.entity.ReviewCategoryTag;
import com.cafe.dashboard.entity.Store;
import com.cafe.dashboard.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final StoreRepository storeRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewCategoryTagRepository tagRepository;
    private final MarketReportRepository marketReportRepository;
    private final MarketReportMetricRepository marketReportMetricRepository;

    public record Overview(
            long storeCount,
            long reviewCount,
            double avgRating,
            List<CategoryStat> categoryStats,
            List<ReviewCategoryTag> topTags,
            List<StoreRankRow> topRatedStores,
            MarketReport marketReport,
            List<MarketReportMetric> marketReportMetrics
    ) {}

    public record StoreRankRow(Store store, double avgRating, long reviewCount) {}

    public Overview loadOverview() {
        long storeCount = storeRepository.count();
        long reviewCount = reviewRepository.count();
        Double avgRatingObj = reviewRepository.averageRatingOverall();
        double avgRating = avgRatingObj == null ? 0.0 : avgRatingObj;

        List<CategoryStat> categoryStats = tagRepository.aggregateByCategory();
        List<ReviewCategoryTag> topTags = tagRepository.findTopTags(PageRequest.of(0, 15));

        Map<String, Store> storeMap = new HashMap<>();
        storeRepository.findAll().forEach(s -> storeMap.put(s.getStoreId(), s));

        List<StoreRankRow> topRatedStores = reviewRepository.aggregateRatingsByStore().stream()
                .filter(stat -> stat.getReviewCount() >= 5)
                .sorted((a, b) -> Double.compare(b.getAvgRating(), a.getAvgRating()))
                .limit(10)
                .map(stat -> new StoreRankRow(storeMap.get(stat.getStoreId()), stat.getAvgRating(), stat.getReviewCount()))
                .filter(row -> row.store() != null)
                .toList();

        MarketReport marketReport = marketReportRepository.findFirstByOrderByReportIdDesc().orElse(null);
        List<MarketReportMetric> metrics = marketReport == null
                ? List.of()
                : marketReportMetricRepository.findByReportIdOrderById(marketReport.getReportId());

        return new Overview(storeCount, reviewCount, avgRating, categoryStats, topTags, topRatedStores,
                marketReport, metrics);
    }
}
