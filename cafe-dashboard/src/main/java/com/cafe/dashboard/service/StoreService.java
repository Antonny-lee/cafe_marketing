package com.cafe.dashboard.service;

import com.cafe.dashboard.entity.*;
import com.cafe.dashboard.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final MenuRepository menuRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewCategoryTagRepository tagRepository;
    private final StoreIntroRepository storeIntroRepository;
    private final StoreInfoItemRepository storeInfoItemRepository;
    private final AiBriefingRepository aiBriefingRepository;

    public record StoreListRow(Store store, Double avgRating, Long reviewCount) {}

    public List<StoreListRow> search(String keyword) {
        List<Store> stores = storeRepository.search(keyword == null ? "" : keyword.trim());
        Map<String, StoreRatingStat> stats = new HashMap<>();
        reviewRepository.aggregateRatingsByStore().forEach(s -> stats.put(s.getStoreId(), s));
        return stores.stream()
                .map(s -> {
                    StoreRatingStat stat = stats.get(s.getStoreId());
                    return new StoreListRow(s, stat == null ? null : stat.getAvgRating(),
                            stat == null ? 0L : stat.getReviewCount());
                })
                .toList();
    }

    public record StoreDetail(
            Store store,
            StoreIntro intro,
            List<Menu> menus,
            Page<Review> reviews,
            List<ReviewCategoryTag> tags,
            Map<String, List<StoreInfoItem>> infoBySection,
            List<AiBriefing> aiBriefings,
            Double avgRating,
            long reviewCount
    ) {}

    public StoreDetail loadDetail(String storeId, int reviewPage) {
        Store store = storeRepository.findById(storeId).orElseThrow();
        StoreIntro intro = storeIntroRepository.findById(storeId).orElse(null);
        List<Menu> menus = menuRepository.findByStoreIdOrderByMenuId(storeId);
        Page<Review> reviews = reviewRepository.findByStoreIdOrderByReviewDateDesc(
                storeId, PageRequest.of(reviewPage, 20, Sort.by(Sort.Direction.DESC, "reviewDate")));
        List<ReviewCategoryTag> tags = tagRepository.findByIdStoreIdOrderByMentionCountDesc(storeId);
        List<StoreInfoItem> infoItems = storeInfoItemRepository.findByStoreIdOrderBySectionAscIdAsc(storeId);
        Map<String, List<StoreInfoItem>> infoBySection = new java.util.LinkedHashMap<>();
        for (StoreInfoItem item : infoItems) {
            infoBySection.computeIfAbsent(item.getSection(), k -> new java.util.ArrayList<>()).add(item);
        }
        List<AiBriefing> briefings = aiBriefingRepository.findByStoreIdOrderById(storeId);

        long reviewCount = reviewRepository.countByStoreId(storeId);
        Double avgRating = reviewRepository.aggregateRatingsByStore().stream()
                .filter(s -> s.getStoreId().equals(storeId))
                .map(StoreRatingStat::getAvgRating)
                .findFirst().orElse(null);

        return new StoreDetail(store, intro, menus, reviews, tags, infoBySection, briefings, avgRating, reviewCount);
    }

    public record ReviewsPage(Store store, Page<Review> reviews, Double avgRating, long reviewCount) {}

    public ReviewsPage loadReviews(String storeId, int page) {
        Store store = storeRepository.findById(storeId).orElseThrow();
        Page<Review> reviews = reviewRepository.findByStoreIdOrderByReviewDateDesc(
                storeId, PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "reviewDate")));
        long reviewCount = reviewRepository.countByStoreId(storeId);
        Double avgRating = reviewRepository.aggregateRatingsByStore().stream()
                .filter(s -> s.getStoreId().equals(storeId))
                .map(StoreRatingStat::getAvgRating)
                .findFirst().orElse(null);
        return new ReviewsPage(store, reviews, avgRating, reviewCount);
    }
}
