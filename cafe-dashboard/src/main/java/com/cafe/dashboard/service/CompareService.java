package com.cafe.dashboard.service;

import com.cafe.dashboard.entity.AiBriefing;
import com.cafe.dashboard.entity.Menu;
import com.cafe.dashboard.entity.Review;
import com.cafe.dashboard.entity.ReviewCategoryTag;
import com.cafe.dashboard.entity.Store;
import com.cafe.dashboard.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompareService {

    private final StoreRepository storeRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewCategoryTagRepository tagRepository;
    private final AiBriefingRepository aiBriefingRepository;
    private final MenuRepository menuRepository;
    private final StoreIntroRepository storeIntroRepository;
    private final WordCloudService wordCloudService;

    public record StoreSummary(
            Store store,
            Double avgRating,
            long reviewCount,
            List<ReviewCategoryTag> topTags,
            String briefing,
            List<Review> sampleReviews,
            String introText,
            List<Menu> topMenu,
            List<WordCloudService.WordEntry> wordCloud,
            String vsDeltaText
    ) {}

    public record PeerStats(long myReviewCount, double avgReviewCount, int percentileRank, int totalStores,
                             int avgPercentile, int rank) {}

    public record TagBar(String storeId, String storeName, int count, int widthPercent, boolean mine, int seriesIndex) {}

    public record TagRow(String tagText, List<TagBar> bars) {}

    public record MapMarker(String storeId, String name, Double lat, Double lng, boolean mine) {}

    public record CompareResult(
            StoreSummary mine,
            List<StoreSummary> rivals,
            PeerStats peerStats,
            List<TagRow> tagComparison,
            List<MapMarker> mapMarkers
    ) {}

    public List<Store> allStores() {
        return storeRepository.findAll();
    }

    public CompareResult compare(String mineId, List<String> rivalIds) {
        List<StoreRatingStat> allStats = reviewRepository.aggregateRatingsByStore();
        Map<String, StoreRatingStat> statByStore = allStats.stream()
                .collect(Collectors.toMap(StoreRatingStat::getStoreId, s -> s));

        StoreSummary mine = buildSummary(mineId, statByStore, null);
        List<StoreSummary> rivals = rivalIds.stream()
                .map(id -> buildSummary(id, statByStore, mine))
                .toList();

        PeerStats peerStats = buildPeerStats(mineId, allStats);
        List<TagRow> tagComparison = buildTagComparison(mine, rivals);
        List<MapMarker> mapMarkers = buildMapMarkers(mine, rivals);

        return new CompareResult(mine, rivals, peerStats, tagComparison, mapMarkers);
    }

    private List<MapMarker> buildMapMarkers(StoreSummary mine, List<StoreSummary> rivals) {
        List<MapMarker> markers = new ArrayList<>();
        Store mineStore = mine.store();
        if (mineStore.getLat() != null && mineStore.getLng() != null) {
            markers.add(new MapMarker(mineStore.getStoreId(), mineStore.getName(), mineStore.getLat(), mineStore.getLng(), true));
        }
        for (StoreSummary r : rivals) {
            Store s = r.store();
            if (s.getLat() != null && s.getLng() != null) {
                markers.add(new MapMarker(s.getStoreId(), s.getName(), s.getLat(), s.getLng(), false));
            }
        }
        return markers;
    }

    private StoreSummary buildSummary(String storeId, Map<String, StoreRatingStat> statByStore, StoreSummary compareAgainst) {
        Store store = storeRepository.findById(storeId).orElseThrow();
        StoreRatingStat stat = statByStore.get(storeId);
        Double avgRating = stat == null ? null : stat.getAvgRating();
        long reviewCount = stat == null ? 0 : stat.getReviewCount();

        List<ReviewCategoryTag> topTags = tagRepository.findByIdStoreIdOrderByMentionCountDesc(storeId)
                .stream().limit(5).toList();

        List<AiBriefing> briefings = aiBriefingRepository.findByStoreIdOrderById(storeId);
        String briefing = briefings.isEmpty() ? null
                : briefings.stream().map(AiBriefing::getSentence).limit(2).collect(Collectors.joining(" "));

        List<Review> sampleReviews = reviewRepository
                .findByStoreIdOrderByReviewDateDesc(storeId, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "reviewDate")))
                .getContent();

        String introText = storeIntroRepository.findById(storeId).map(i -> i.getIntroText()).orElse(null);
        List<Menu> topMenu = menuRepository.findByStoreIdOrderByMenuId(storeId).stream().limit(4).toList();
        List<WordCloudService.WordEntry> wordCloud = wordCloudService.compute(storeId);

        String vsDeltaText = compareAgainst == null ? null : buildVsDeltaText(compareAgainst, avgRating, reviewCount);

        return new StoreSummary(store, avgRating, reviewCount, topTags, briefing, sampleReviews,
                introText, topMenu, wordCloud, vsDeltaText);
    }

    private String buildVsDeltaText(StoreSummary mine, Double rivalRating, long rivalReviewCount) {
        StringBuilder sb = new StringBuilder();
        if (mine.avgRating() != null && rivalRating != null) {
            double diff = Math.round((rivalRating - mine.avgRating()) * 100) / 100.0;
            if (diff > 0) sb.append("평점 +").append(diff).append(" ");
            else if (diff < 0) sb.append("평점 ").append(diff).append(" ");
            else sb.append("평점 동일 ");
        }
        long reviewDiff = rivalReviewCount - mine.reviewCount();
        if (reviewDiff > 0) sb.append("· 리뷰 +").append(reviewDiff).append("건 더 많음 (우리 대비)");
        else if (reviewDiff < 0) sb.append("· 리뷰 ").append(Math.abs(reviewDiff)).append("건 더 적음 (우리 대비)");
        else sb.append("· 리뷰 수 동일");
        return sb.toString();
    }

    private PeerStats buildPeerStats(String mineId, List<StoreRatingStat> allStats) {
        List<Long> counts = allStats.stream().map(StoreRatingStat::getReviewCount).sorted().toList();
        long myCount = allStats.stream()
                .filter(s -> s.getStoreId().equals(mineId))
                .map(StoreRatingStat::getReviewCount)
                .findFirst().orElse(0L);

        double avg = counts.stream().mapToLong(Long::longValue).average().orElse(0);
        long below = counts.stream().filter(c -> c <= myCount).count();
        int percentile = counts.isEmpty() ? 0 : (int) Math.round(100.0 * below / counts.size());
        long belowAvg = counts.stream().filter(c -> c <= avg).count();
        int avgPercentile = counts.isEmpty() ? 0 : (int) Math.round(100.0 * belowAvg / counts.size());
        long higherCount = counts.stream().filter(c -> c > myCount).count();
        int rank = (int) higherCount + 1;

        return new PeerStats(myCount, avg, percentile, counts.size(), avgPercentile, rank);
    }

    private List<TagRow> buildTagComparison(StoreSummary mine, List<StoreSummary> rivals) {
        List<String> tagTexts = mine.topTags().stream().map(t -> t.getId().getTagText()).toList();
        if (tagTexts.isEmpty()) return List.of();

        List<StoreSummary> all = new ArrayList<>();
        all.add(mine);
        all.addAll(rivals);

        List<String> storeIds = all.stream().map(s -> s.store().getStoreId()).toList();
        List<ReviewCategoryTag> tags = tagRepository.findByIdStoreIdInAndIdTagTextIn(storeIds, tagTexts);

        Map<String, Map<String, Integer>> byTag = new LinkedHashMap<>();
        for (String tagText : tagTexts) {
            byTag.put(tagText, new LinkedHashMap<>());
        }
        for (ReviewCategoryTag t : tags) {
            byTag.get(t.getId().getTagText()).put(t.getId().getStoreId(), t.getMentionCount());
        }

        return tagTexts.stream().map(tagText -> {
            Map<String, Integer> counts = byTag.get(tagText);
            int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(1);
            List<TagBar> bars = new ArrayList<>();
            for (int i = 0; i < all.size(); i++) {
                StoreSummary s = all.get(i);
                String sid = s.store().getStoreId();
                int count = counts.getOrDefault(sid, 0);
                int width = max == 0 ? 0 : (int) Math.round(100.0 * count / max);
                bars.add(new TagBar(sid, s.store().getName(), count, width, sid.equals(mine.store().getStoreId()), i));
            }
            return new TagRow(tagText, bars);
        }).toList();
    }
}
