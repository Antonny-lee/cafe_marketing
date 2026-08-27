package com.cafe.dashboard.service;

import com.cafe.dashboard.entity.Menu;
import com.cafe.dashboard.entity.MarketReport;
import com.cafe.dashboard.entity.ReviewCategoryTag;
import com.cafe.dashboard.entity.Store;
import com.cafe.dashboard.openai.OpenAiClient;
import com.cafe.dashboard.openai.OpenAiDtos;
import com.cafe.dashboard.repository.MenuRepository;
import com.cafe.dashboard.repository.ReviewCategoryTagRepository;
import com.cafe.dashboard.repository.ReviewRepository;
import com.cafe.dashboard.repository.StoreIntroRepository;
import com.cafe.dashboard.repository.StoreRatingStat;
import com.cafe.dashboard.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketingChatService {

    private final DashboardService dashboardService;
    private final MarketAreaService marketAreaService;
    private final WordCloudService wordCloudService;
    private final StoreRepository storeRepository;
    private final StoreIntroRepository storeIntroRepository;
    private final MenuRepository menuRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewCategoryTagRepository tagRepository;
    private final OpenAiClient openAiClient;

    public record ChatTurn(String role, String content) {}

    public String ask(String storeId, List<ChatTurn> history, String userMessage) {
        List<OpenAiDtos.ChatMessage> messages = new ArrayList<>();
        messages.add(new OpenAiDtos.ChatMessage("system", buildSystemPrompt(storeId)));
        for (ChatTurn turn : history) {
            messages.add(new OpenAiDtos.ChatMessage(turn.role(), turn.content()));
        }
        messages.add(new OpenAiDtos.ChatMessage("user", userMessage));

        return openAiClient.chat(messages);
    }

    private String buildSystemPrompt(String storeId) {
        Store myStore = storeRepository.findById(storeId).orElseThrow();
        long myReviewCount = reviewRepository.countByStoreId(storeId);
        Double myAvgRating = reviewRepository.aggregateRatingsByStore().stream()
                .filter(s -> s.getStoreId().equals(storeId))
                .map(StoreRatingStat::getAvgRating)
                .findFirst().orElse(null);
        List<ReviewCategoryTag> myTags = tagRepository.findByIdStoreIdOrderByMentionCountDesc(storeId);
        List<WordCloudService.WordEntry> wordCloud = wordCloudService.compute(storeId);
        List<Menu> menus = menuRepository.findByStoreIdOrderByMenuId(storeId);
        String introText = storeIntroRepository.findById(storeId).map(i -> i.getIntroText()).orElse(null);

        DashboardService.Overview overview = dashboardService.loadOverview();
        MarketAreaService.MarketAreaOverview market = marketAreaService.load();

        StringBuilder sb = new StringBuilder();
        sb.append("당신은 '단골장부' 대시보드에 연결된 카페 마케팅 어드바이저입니다. ")
                .append("지금 대화하는 사장님의 매장은 아래 [내 매장]에 지정된 곳 하나뿐입니다. ")
                .append("반드시 이 매장 기준으로 답하고, 다른 매장 이야기는 비교 목적일 때만 참고로 언급하세요.\n\n")
                .append("중요한 답변 규칙 (사장님이 마케팅 전략/아이디어를 물어볼 때는 아래 4단계 구성으로 풍성하게 답하세요. ")
                .append("단순 인사나 잡담에는 이 구성을 강제하지 않아도 됩니다):\n\n")
                .append("형식 규칙: 각 단계는 반드시 \"### N. 제목\" 형태로 제목 줄을 먼저 단독으로 쓰고, ")
                .append("그 다음 줄부터 설명 문장을 이어가세요. 제목과 설명을 같은 줄에 대시(—)로 붙여쓰지 마세요. ")
                .append("예시:\n### 1. 우리 매장 진단\n실제 설명 문장이 여기부터 시작합니다...\n\n")
                .append("강조 규칙: 핵심 단어(실제 메뉴명, 키워드, 숫자 등)는 **단어** 형태로 볼드 처리하고, ")
                .append("사장님이 특히 눈여겨봐야 할 핵심 문장 전체는 ==문장== 형태로 형광펜 하이라이트 처리하세요. ")
                .append("답변마다 하이라이트 문장은 1~2개 정도로만, 정말 중요한 것만 골라서 남발하지 마세요.\n\n")
                .append("1) 우리 매장 진단 — 아래 데이터(메뉴, 가격, 소개글, 영업시간, 리뷰 키워드, 워드클라우드, 상권분석)를 실제로 인용해서 ")
                .append("지금 무엇이 잘 팔리고/자주 언급되는지 짚어주세요. '시원한 여름 메뉴를 출시하세요' 같은 데이터 무시한 일반론은 금지입니다. ")
                .append("반드시 \"지금 [실제 메뉴명/키워드]가 [근거: 리뷰 태그 N건/워드클라우드 등장]로 인기가 있다\"는 식으로 우리 매장 데이터를 출발점으로 삼으세요.\n\n")
                .append("2) 참고할 만한 트렌드·사례 — 카페/디저트 업계에서 최근 흔히 통하는 트렌드(예: 특정 재료 유행, 인스타 감성 플레이팅, 시즌 한정 마케팅 방식)나 ")
                .append("비슷한 매장들이 자주 쓰는 성공 패턴을 소개하세요. 이건 우리 매장 실제 데이터가 아니라 업계에 널리 알려진 일반적 지식이므로, ")
                .append("\"업계에서 흔히 알려진 사례로는~\" \"요즘 카페 트렌드는~\" 같은 표현으로 출처를 분명히 구분해서 말하고, 특정 실존 매장명을 사실인 것처럼 단정하지 마세요.\n\n")
                .append("3) 트렌드를 우리 매장에 접목 — 2)에서 소개한 트렌드를 1)의 우리 매장 실제 메뉴/컨셉/강점과 연결해서, ")
                .append("우리 매장에 맞게 변형한 구체적 실행 아이디어(메뉴명, 가격대, 진행 방식)를 제시하세요.\n\n")
                .append("4) 타겟 고객층 공략 — 아래 [상권 타겟 고객층 데이터]를 근거로 주 타겟층 특성을 짚고, ")
                .append("그 타겟층에 맞는 톤·채널(인스타/네이버플레이스 등)·이벤트 방식을 제안하세요.\n\n")
                .append("전체적으로 데이터에 없는 사실을 지어내지 말고, 추측이거나 일반 지식이면 그렇다고 자연스럽게 밝히세요.\n\n");

        sb.append("[내 매장]\n")
                .append("이름: ").append(myStore.getName()).append("\n")
                .append("주소: ").append(myStore.getAddress()).append("\n")
                .append("영업시간: ").append(myStore.getBusinessHours() == null ? "정보 없음" : myStore.getBusinessHours()).append("\n")
                .append("평균 평점: ").append(myAvgRating == null ? "리뷰 없음" : String.format("%.2f", myAvgRating)).append("\n")
                .append("리뷰 수: ").append(myReviewCount).append("\n\n");

        if (introText != null && !introText.isBlank()) {
            sb.append("[매장 소개글]\n").append(introText).append("\n\n");
        }

        sb.append("[판매 메뉴 및 가격]\n");
        if (menus.isEmpty()) {
            sb.append("- 등록된 메뉴 정보가 없습니다.\n");
        } else {
            for (Menu m : menus) {
                String price = m.getPriceKrw() != null ? m.getPriceKrw() + "원" : (m.getPriceNote() != null ? m.getPriceNote() : "가격 정보 없음");
                sb.append("- ").append(m.getMenuName()).append(" (").append(price).append(")\n");
            }
        }

        sb.append("\n[리뷰 키워드 TOP - 손님들이 실제로 좋아하는 포인트]\n");
        if (myTags.isEmpty()) {
            sb.append("- 아직 집계된 리뷰 태그가 없습니다.\n");
        } else {
            for (ReviewCategoryTag tag : myTags.subList(0, Math.min(15, myTags.size()))) {
                sb.append("- ").append(tag.getId().getTagText())
                        .append(" (").append(tag.getMentionCount()).append("회, 카테고리: ")
                        .append(tag.getTagCategory()).append(")\n");
            }
        }

        sb.append("\n[리뷰 원문에서 자주 등장한 단어 - 워드클라우드]\n");
        if (wordCloud.isEmpty()) {
            sb.append("- 아직 데이터가 부족합니다.\n");
        } else {
            for (WordCloudService.WordEntry w : wordCloud) {
                sb.append("- ").append(w.word()).append(" (").append(w.count()).append("회)\n");
            }
        }

        sb.append("\n[비교 참고: 전체 매장 현황]\n")
                .append("등록 매장 수: ").append(overview.storeCount())
                .append(", 전체 평균 평점: ").append(String.format("%.2f", overview.avgRating())).append("\n");

        MarketReport report = market.report();
        if (report != null) {
            sb.append("\n[상권분석 리포트: ").append(report.getLocation())
                    .append(" / ").append(report.getIndustry())
                    .append(" / ").append(report.getQuarter()).append("]\n");
            if (report.getOpinionText() != null) {
                sb.append(report.getOpinionText()).append("\n");
            }
        }

        sb.append("\n[상권 타겟 고객층 데이터]\n");
        if (market.genderDonut() != null) {
            sb.append("- 지역 전체 매출 중 여성 비중: ").append(String.format("%.0f", market.genderDonut().value())).append("%\n");
        }
        if (!market.industryDonuts().isEmpty()) {
            sb.append("- 업종별 여성 매출 비중:\n");
            for (MarketAreaService.DonutItem d : market.industryDonuts()) {
                sb.append("  · ").append(d.label()).append(": 여성 ").append(String.format("%.0f", d.value())).append("%\n");
            }
        }
        if (market.genderDonut() == null && market.industryDonuts().isEmpty()) {
            sb.append("- 타겟 고객층 관련 상권 데이터가 아직 없습니다.\n");
        }

        return sb.toString();
    }
}
