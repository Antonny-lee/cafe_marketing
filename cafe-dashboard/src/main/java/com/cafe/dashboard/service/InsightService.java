package com.cafe.dashboard.service;

import com.cafe.dashboard.entity.Review;
import com.cafe.dashboard.entity.ReviewInsight;
import com.cafe.dashboard.entity.ReviewInsightComparison;
import com.cafe.dashboard.entity.ReviewInsightItem;
import com.cafe.dashboard.entity.ReviewInsightKeypoint;
import com.cafe.dashboard.openai.OpenAiClient;
import com.cafe.dashboard.openai.OpenAiDtos;
import com.cafe.dashboard.repository.ReviewInsightComparisonRepository;
import com.cafe.dashboard.repository.ReviewInsightItemRepository;
import com.cafe.dashboard.repository.ReviewInsightKeypointRepository;
import com.cafe.dashboard.repository.ReviewInsightRepository;
import com.cafe.dashboard.repository.ReviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InsightService {

    private static final Logger log = LoggerFactory.getLogger(InsightService.class);
    private static final int SAMPLE_SIZE = 40;

    private final ReviewRepository reviewRepository;
    private final ReviewInsightRepository insightRepository;
    private final ReviewInsightItemRepository insightItemRepository;
    private final ReviewInsightKeypointRepository insightKeypointRepository;
    private final ReviewInsightComparisonRepository insightComparisonRepository;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;
    private final WordCloudService wordCloudService;
    private final CompareService compareService;

    public Optional<ReviewInsight> getCached(String storeId) {
        return insightRepository.findById(storeId);
    }

    public List<ReviewInsightItem> getCachedItems(String storeId) {
        return insightItemRepository.findByStoreIdOrderById(storeId);
    }

    public List<ReviewInsightKeypoint> getCachedKeypoints(String storeId) {
        return insightKeypointRepository.findByStoreIdOrderById(storeId);
    }

    public List<ReviewInsightComparison> getCachedComparisons(String storeId, List<String> rivalIds) {
        if (rivalIds == null || rivalIds.isEmpty()) return List.of();
        return insightComparisonRepository.findByStoreIdAndRivalStoreIdIn(storeId, rivalIds);
    }

    @Transactional
    public ReviewInsight analyze(String storeId, List<String> rivalIds) {
        List<Review> sample = reviewRepository
                .findByStoreIdOrderByReviewDateDesc(storeId, PageRequest.of(0, SAMPLE_SIZE, Sort.by(Sort.Direction.DESC, "reviewDate")))
                .getContent()
                .stream()
                .filter(r -> r.getReviewText() != null && !r.getReviewText().isBlank())
                .toList();

        if (sample.isEmpty()) {
            throw new IllegalStateException("분석할 리뷰 원문이 없습니다.");
        }

        List<String> rivals = rivalIds == null ? List.of() : rivalIds;
        CompareService.CompareResult compareResult = compareService.compare(storeId, rivals);

        List<WordCloudService.WordEntry> topWords = wordCloudService.compute(storeId);
        String userPrompt = buildPrompt(sample, topWords, compareResult);
        String json = openAiClient.chatJson(SYSTEM_PROMPT, userPrompt);
        log.info("OpenAI raw response for storeId={} rivals={}: {}", storeId, rivals, json);

        OpenAiDtos.InsightPayload payload;
        try {
            payload = objectMapper.readValue(json, OpenAiDtos.InsightPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException("OpenAI 응답 파싱 실패: " + e.getMessage(), e);
        }

        ReviewInsight insight = insightRepository.findById(storeId).orElseGet(ReviewInsight::new);
        insight.setStoreId(storeId);
        insight.setPositiveRatio(payload.positive_ratio());
        insight.setNegativeRatio(payload.negative_ratio());
        insight.setAnalyzedCount(sample.size());
        insight.setWordSummary(payload.word_summary());
        insight.setAnalyzedAt(LocalDateTime.now());
        insightRepository.save(insight);

        insightItemRepository.deleteByStoreId(storeId);
        if (payload.insights() != null) {
            List<ReviewInsightItem> items = payload.insights().stream().map(i -> {
                ReviewInsightItem item = new ReviewInsightItem();
                item.setStoreId(storeId);
                item.setQuote(i.quote());
                item.setSuggestion(i.suggestion());
                return item;
            }).toList();
            insightItemRepository.saveAll(items);
        }

        insightKeypointRepository.deleteByStoreId(storeId);
        if (payload.key_points() != null) {
            List<ReviewInsightKeypoint> keypoints = payload.key_points().stream().map(k -> {
                ReviewInsightKeypoint kp = new ReviewInsightKeypoint();
                kp.setStoreId(storeId);
                kp.setIcon(k.icon());
                kp.setText(k.text());
                return kp;
            }).toList();
            insightKeypointRepository.saveAll(keypoints);
        }

        if (!rivals.isEmpty() && (payload.competitor_comparisons() == null || payload.competitor_comparisons().isEmpty())) {
            log.warn("OpenAI returned no competitor_comparisons for storeId={} rivals={} (raw json above)", storeId, rivals);
        }

        if (payload.competitor_comparisons() != null) {
            for (OpenAiDtos.CompetitorComparison c : payload.competitor_comparisons()) {
                String matchedRivalId = matchRivalId(c.rival_store_id(), rivals);
                if (matchedRivalId == null) {
                    log.warn("Skipping competitor_comparison with unmatched rival_store_id='{}' (expected one of {})",
                            c.rival_store_id(), rivals);
                    continue;
                }
                ReviewInsightComparison comparison = insightComparisonRepository
                        .findByStoreIdAndRivalStoreId(storeId, matchedRivalId)
                        .orElseGet(ReviewInsightComparison::new);
                comparison.setStoreId(storeId);
                comparison.setRivalStoreId(matchedRivalId);
                comparison.setStrength(c.strength());
                comparison.setDifference(c.difference());
                comparison.setAnalyzedAt(LocalDateTime.now());
                insightComparisonRepository.save(comparison);
            }
        }

        return insight;
    }

    /** The model occasionally wraps/pads the id (extra spaces, quotes) - match loosely. */
    private String matchRivalId(String candidate, List<String> rivals) {
        if (candidate == null) return null;
        String cleaned = candidate.trim().replaceAll("^['\"]|['\"]$", "");
        return rivals.stream().filter(r -> r.equalsIgnoreCase(cleaned)).findFirst().orElse(null);
    }

    private String buildPrompt(List<Review> reviews, List<WordCloudService.WordEntry> topWords,
                                CompareService.CompareResult compareResult) {
        String reviewLines = reviews.stream()
                .map(r -> "- (평점 %s) %s".formatted(
                        r.getRating() == null ? "없음" : r.getRating(),
                        r.getReviewText().replace("\n", " ")))
                .collect(Collectors.joining("\n"));

        String wordLine = topWords.stream()
                .map(w -> w.word() + "(" + w.count() + "건)")
                .collect(Collectors.joining(", "));

        String mineTagLine = compareResult.mine().topTags().stream()
                .map(t -> t.getId().getTagText() + "(" + t.getMentionCount() + "건)")
                .collect(Collectors.joining(", "));

        String myBlock = """
                [우리 매장 정보]
                이름: %s / 평균 평점: %s / 리뷰 수: %d개
                강점 키워드: %s
                AI 브리핑(네이버 지도 요약): %s
                """.formatted(
                compareResult.mine().store().getName(),
                compareResult.mine().avgRating(),
                compareResult.mine().reviewCount(),
                mineTagLine,
                compareResult.mine().briefing() == null ? "없음" : compareResult.mine().briefing());

        String rivalBlock = compareResult.rivals().isEmpty() ? "" : "\n[비교할 경쟁 매장 정보]\n" +
                compareResult.rivals().stream().map(r -> {
                    String tagLine = r.topTags().stream()
                            .map(t -> t.getId().getTagText() + "(" + t.getMentionCount() + "건)")
                            .collect(Collectors.joining(", "));
                    String rivalWordLine = r.wordCloud().stream()
                            .map(w -> w.word() + "(" + w.count() + "건)")
                            .collect(Collectors.joining(", "));
                    String rivalReviewLines = r.sampleReviews().stream()
                            .filter(rv -> rv.getReviewText() != null && !rv.getReviewText().isBlank())
                            .limit(3)
                            .map(rv -> "    - " + rv.getReviewText().replace("\n", " "))
                            .collect(Collectors.joining("\n"));
                    return """
                            - rival_store_id=%s / 이름: %s / 평균 평점: %s / 리뷰 수: %d개
                              강점 키워드: %s
                              자주 등장한 단어: %s
                              AI 브리핑(네이버 지도 요약): %s
                              실제 리뷰 원문 일부:
                            %s
                            """.formatted(
                            r.store().getStoreId(), r.store().getName(), r.avgRating(), r.reviewCount(),
                            tagLine, rivalWordLine, r.briefing() == null ? "없음" : r.briefing(), rivalReviewLines);
                }).collect(Collectors.joining("\n"))
                + "\n위에 나온 rival_store_id " + compareResult.rivals().size() + "개(" +
                compareResult.rivals().stream().map(r -> r.store().getStoreId()).collect(Collectors.joining(", ")) +
                ") 전부에 대해 competitor_comparisons 배열에 각각 항목을 하나씩, 총 " + compareResult.rivals().size() +
                "개 반드시 만들어야 합니다. 절대 빈 배열로 응답하지 마세요.\n";

        return """
                아래는 한 카페(우리 매장)의 실제 방문자 리뷰 원문 %d건, 자주 등장한 단어 목록, 그리고 우리 매장/경쟁 매장 정보입니다. 분석해주세요.

                %s
                %s

                [자주 등장한 단어]
                %s

                [리뷰 원문]
                %s
                """.formatted(reviews.size(), myBlock, rivalBlock, wordLine, reviewLines);
    }

    private static final String SYSTEM_PROMPT = """
            너는 소상공인 카페 사장을 돕는 리뷰 분석 어시스턴트야. 주어진 리뷰 원문, 자주 등장한 단어 목록,
            우리 매장/경쟁 매장 정보를 분석해서 반드시 아래 JSON 형식으로만 응답해:
            {
              "positive_ratio": 0~100 사이 숫자 (긍정적인 리뷰의 비율, %),
              "negative_ratio": 0~100 사이 숫자 (부정적인 리뷰의 비율, %),
              "word_summary": "자주 등장한 단어 조합과 실제 리뷰 원문 내용을 근거로, 이 매장이 손님들에게 어떤 이미지로 기억되는지 3~5문장 분량으로 구체적이고 풍성하게 해석. 어떤 단어들이 왜 함께 묶이는지, 그게 어떤 손님층/방문 상황(데이트, 혼자 작업, 모임 등)과 연결되는지, 실제 리뷰에 나온 구체적인 메뉴명이나 공간 특징을 근거로 들어서 사장님이 마케팅 포인트로 바로 써먹을 수 있을 만큼 실질적으로 작성. 추상적인 미사여구로 끝내지 말고 왜 그렇게 판단했는지 근거를 문장 안에 녹여서 설명",
              "key_points": [
                {"icon": "어울리는 이모지 하나", "text": "word_summary의 핵심을 한 줄(15자 내외)로 압축한 문장"}
              ],
              "insights": [
                {"quote": "리뷰 원문 안에 실제로 아쉬움/불편함/애매함이 드러나는 문장만 그대로 인용 (짧게)", "suggestion": "quote에 실제로 적힌 내용에서 바로 이어지는 개선 제안 — quote에 없는 내용을 지어내면 안 됨"}
              ],
              "competitor_comparisons": [
                {
                  "rival_store_id": "[비교할 경쟁 매장 정보]에 주어진 rival_store_id 값을 그대로 사용",
                  "strength": "이 경쟁 매장의 '실제 리뷰 원문'과 'AI 브리핑', '자주 등장한 단어'를 근거로, 손님들이 구체적으로 무엇 때문에 이 매장을 좋아하는지 4~6문장으로 풍성하게 설명. 반드시 리뷰 원문에 나온 실제 메뉴명·표현·에피소드를 최소 1개 이상 직접 인용하거나 언급해서 근거를 보여줄 것. 단순히 '평점이 높고 리뷰가 많다' 같은 숫자 재진술이나 '분위기가 좋고 맛있다' 같은 뻔한 표현은 금지 — 왜 좋은지, 어떤 손님층에게 왜 통하는지까지 파고들어 설명",
                  "difference": "우리 매장과 이 경쟁 매장을 같은 기준(강점 키워드, 자주 등장한 단어, 리뷰 톤, 손님층/방문 목적)으로 나란히 비교해서 4~6문장으로 설명. 두 매장의 키워드/리뷰 원문에서 실제로 드러나는 차이를 근거로 들 것 (예: 한쪽은 '천천히 머무는' 키워드가 강하고 다른 쪽은 '특정 메뉴를 목적으로 방문'하는 키워드가 강하다는 식). 마지막엔 우리 매장이 이 경쟁 매장 대비 어느 지점에서 우위이고 어느 지점에서 밀리는지까지 솔직하게 짚어줄 것"
                }
              ]
            }
            competitor_comparisons는 [비교할 경쟁 매장 정보]에 나온 매장마다 하나씩 반드시 만들어. 경쟁 매장 정보가 없으면 빈 배열로 응답해.
            strength와 difference는 특히 '실제 리뷰 원문'을 근거 삼아 구체적으로 써야 해 — 리뷰에 없는 내용을 지어내지 말고, 주어진 정보 안에서 최대한 실질적이고 사장님이 바로 이해할 수 있게 작성해.
            word_summary는 단어를 단순히 나열하지 말고, 그 조합이 암시하는 하나의 테마나 이미지로 종합해서 설명해.
            key_points는 정확히 3개, 서로 다른 관점(예: 손님이 느끼는 분위기/감성, 자주 언급되는 메뉴, 주로 어떤 목적으로 방문하는지)에서 하나씩 뽑아 - word_summary를 읽지 않고 이 3줄만 봐도 매장 이미지가 바로 그려지게 짧고 구체적으로 작성해.
            insights는 최대 3개까지, 평점은 낮지 않아도 리뷰 원문 안에 실제로 아쉬움·불편함·애매함·바람("~였으면", "그런데", "다만", "아쉬운" 등)이 명시적으로 드러나는 리뷰만 뽑아.
            단순 칭찬이나 좋았다는 내용뿐인 리뷰를 억지로 골라서 있지도 않은 문제를 지어내지 마 — quote에 실제로 없는 불만을 suggestion에서 만들어내는 것은 절대 금지.
            기준에 맞는 리뷰가 3개보다 적으면 그만큼만 담고, 하나도 없으면 insights는 빈 배열로 응답해. 개수를 채우려고 억지로 뽑지 마.
            반드시 유효한 JSON만 출력하고 다른 설명은 하지 마.
            """;
}
