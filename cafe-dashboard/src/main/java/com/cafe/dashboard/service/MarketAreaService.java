package com.cafe.dashboard.service;

import com.cafe.dashboard.entity.MarketReport;
import com.cafe.dashboard.entity.MarketReportBreakdown;
import com.cafe.dashboard.entity.MarketReportMetric;
import com.cafe.dashboard.entity.MarketReportSeries;
import com.cafe.dashboard.repository.MarketReportBreakdownRepository;
import com.cafe.dashboard.repository.MarketReportMetricRepository;
import com.cafe.dashboard.repository.MarketReportRepository;
import com.cafe.dashboard.repository.MarketReportSeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MarketAreaService {

    private final MarketReportRepository marketReportRepository;
    private final MarketReportMetricRepository marketReportMetricRepository;
    private final MarketReportSeriesRepository marketReportSeriesRepository;
    private final MarketReportBreakdownRepository marketReportBreakdownRepository;

    private static final List<String> SERIES_METRICS = List.of("점포수", "매출액", "매출건수", "유동인구밀도");
    private static final List<String> BREAKDOWN_CATEGORIES = List.of(
            "요일별매출", "시간대별매출", "성별매출", "업종별여성매출비중", "연령대별외식업매출");

    public record BreakdownBar(String label, double value, int widthPercent, boolean top) {}

    public record DonutItem(String label, double value, String gradientCss) {}

    public record MarketAreaOverview(
            MarketReport report,
            Map<String, MarketReportMetric> metricsByName,
            Map<String, List<MarketReportSeries>> seriesByMetric,
            Map<String, List<MarketReportBreakdown>> breakdownByCategory,
            Map<String, List<BreakdownBar>> rankedByCategory,
            DonutItem genderDonut,
            List<DonutItem> industryDonuts
    ) {}

    private String genderGradient(double femalePct) {
        double deg = femalePct * 3.6;
        return "background: conic-gradient(#3E4A2A 0deg " + deg + "deg, #D8CDB6 " + deg + "deg 360deg);";
    }

    public MarketAreaOverview load() {
        MarketReport report = marketReportRepository.findFirstByOrderByReportIdDesc().orElse(null);
        if (report == null) {
            return new MarketAreaOverview(null, Map.of(), Map.of(), Map.of(), Map.of(), null, List.of());
        }

        List<MarketReportMetric> metrics = marketReportMetricRepository.findByReportIdOrderById(report.getReportId());
        Map<String, MarketReportMetric> byName = metrics.stream()
                .collect(Collectors.toMap(MarketReportMetric::getMetricName, m -> m, (a, b) -> a));

        Map<String, List<MarketReportSeries>> seriesByMetric = new LinkedHashMap<>();
        for (String metric : SERIES_METRICS) {
            seriesByMetric.put(metric, marketReportSeriesRepository
                    .findByReportIdAndMetricNameOrderById(report.getReportId(), metric));
        }

        Map<String, List<MarketReportBreakdown>> breakdownByCategory = new LinkedHashMap<>();
        for (String category : BREAKDOWN_CATEGORIES) {
            breakdownByCategory.put(category, marketReportBreakdownRepository
                    .findByReportIdAndCategoryOrderById(report.getReportId(), category));
        }

        Map<String, List<BreakdownBar>> rankedByCategory = new LinkedHashMap<>();
        for (String category : List.of("요일별매출", "연령대별외식업매출")) {
            List<MarketReportBreakdown> sorted = breakdownByCategory.get(category).stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .toList();
            double max = sorted.isEmpty() ? 1 : sorted.get(0).getValue();
            List<BreakdownBar> bars = new ArrayList<>();
            for (int i = 0; i < sorted.size(); i++) {
                MarketReportBreakdown b = sorted.get(i);
                bars.add(new BreakdownBar(b.getLabel(), b.getValue(),
                        (int) Math.round(100.0 * b.getValue() / max), i == 0));
            }
            rankedByCategory.put(category, bars);
        }

        DonutItem genderDonut = null;
        List<MarketReportBreakdown> genderRows = breakdownByCategory.get("성별매출");
        if (!genderRows.isEmpty()) {
            double femalePct = genderRows.get(0).getValue();
            genderDonut = new DonutItem("여성", femalePct, genderGradient(femalePct));
        }

        List<DonutItem> industryDonuts = breakdownByCategory.get("업종별여성매출비중").stream()
                .map(b -> new DonutItem(b.getLabel(), b.getValue(), genderGradient(b.getValue())))
                .toList();

        return new MarketAreaOverview(report, byName, seriesByMetric, breakdownByCategory, rankedByCategory,
                genderDonut, industryDonuts);
    }
}
