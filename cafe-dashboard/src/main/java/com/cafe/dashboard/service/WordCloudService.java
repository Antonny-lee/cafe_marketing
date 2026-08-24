package com.cafe.dashboard.service;

import com.cafe.dashboard.entity.Review;
import com.cafe.dashboard.repository.ReviewRepository;
import com.cafe.dashboard.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Naive Korean word-frequency word cloud — no LLM/external API involved
 * (just tokenizing + trailing-particle stripping + a stopword list), so it's
 * safe to compute on every page view without touching the shared OpenAI key.
 */
@Service
@RequiredArgsConstructor
public class WordCloudService {

    private static final int MAX_WORDS = 14;
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[\\s,.!?~()\\[\\]{}\"'·\\-/:;\\\\|<>0-9]+");

    // Longest-first so we strip "습니다"/"에서" before shorter overlapping suffixes like "다"/"서".
    private static final String[] PARTICLE_SUFFIXES = {
            "스러워요", "했어요", "이에요", "예요", "에서", "으로", "부터", "까지", "이라",
            "네요", "어요", "아요", "라서", "지만", "인데", "구요", "고요",
            "은", "는", "이", "가", "을", "를", "의", "에", "로", "와", "과", "도", "만", "요", "다", "게"
    };

    private static final Set<String> STOPWORDS = Set.of(
            "정말", "너무", "진짜", "그냥", "여기", "거기", "저희", "우리", "이거", "저거", "그거",
            "그리고", "그래서", "하지만", "근데", "이런", "저런", "그런", "많이", "조금", "매우",
            "카페", "매장", "사장님", "방문", "이용", "느낌", "생각", "정도", "가지", "때문",
            "완전", "약간", "다시", "먼저", "바로", "역시", "제일", "가장", "오늘"
    );

    private final ReviewRepository reviewRepository;
    private final StoreRepository storeRepository;

    // (top%, left%) scatter slots, roughly matching the reference mockup's layout.
    private static final int[][] SLOTS = {
            {10, 4}, {8, 54}, {18, 78}, {40, 30}, {36, 2}, {56, 60}, {64, 10},
            {76, 56}, {80, 30}, {26, 60}, {50, 82}, {68, 78}, {14, 32}, {46, 50}
    };
    private static final String[] COLORS = {"#3E4A2A", "#241D14", "#5C5245"};

    public record WordEntry(String word, int count, int rank, int topPercent, int leftPercent,
                             int fontSizePx, String color, int rotateDeg) {}

    public List<WordEntry> compute(String storeId) {
        String storeName = storeRepository.findById(storeId).map(s -> s.getName()).orElse(null);
        return computeFrom(reviewRepository.findByStoreId(storeId), storeName);
    }

    private List<WordEntry> computeFrom(List<Review> reviews, String storeName) {
        Map<String, Integer> counts = new HashMap<>();
        for (Review r : reviews) {
            String text = r.getReviewText();
            if (text == null || text.isBlank()) continue;
            for (String raw : TOKEN_SPLIT.split(text)) {
                String word = stripParticle(raw);
                if (word.length() < 2 || STOPWORDS.contains(word)) continue;
                if (storeName != null && (storeName.contains(word) || word.contains(storeName))) continue;
                counts.merge(word, 1, Integer::sum);
            }
        }

        List<Map.Entry<String, Integer>> sorted = counts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(MAX_WORDS)
                .toList();

        List<WordEntry> result = new ArrayList<>();
        int rank = 0;
        int maxCount = sorted.isEmpty() ? 1 : sorted.get(0).getValue();
        for (Map.Entry<String, Integer> e : sorted) {
            int[] slot = SLOTS[rank % SLOTS.length];
            int fontSize = 13 + (int) Math.round(17.0 * e.getValue() / maxCount);
            int rotate = ((rank * 37) % 13) - 6;
            String color = COLORS[rank % COLORS.length];
            result.add(new WordEntry(e.getKey(), e.getValue(), rank, slot[0], slot[1], fontSize, color, rotate));
            rank++;
        }
        return result;
    }

    private String stripParticle(String token) {
        String word = token.trim();
        if (word.isEmpty()) return word;
        for (String suffix : PARTICLE_SUFFIXES) {
            if (word.length() > suffix.length() + 1 && word.endsWith(suffix)) {
                return word.substring(0, word.length() - suffix.length());
            }
        }
        return word;
    }
}
