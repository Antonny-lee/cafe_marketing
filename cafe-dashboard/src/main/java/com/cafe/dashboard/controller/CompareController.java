package com.cafe.dashboard.controller;

import com.cafe.dashboard.service.ActiveStoreResolver;
import com.cafe.dashboard.service.CompareService;
import com.cafe.dashboard.service.InsightService;
import com.cafe.dashboard.service.WordCloudService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class CompareController {

    private final CompareService compareService;
    private final InsightService insightService;
    private final ActiveStoreResolver activeStoreResolver;
    private final WordCloudService wordCloudService;

    @Value("${kakao.map-key}")
    private String kakaoMapKey;

    /** 세션에 마지막 비교 상태를 저장해, 다른 탭에 갔다 와도(로그아웃 전까지) 초기화되지 않게 한다. */
    private static final String SESSION_RIVALS = "compare.lastRivals";
    private static final String SESSION_ANALYZED = "compare.lastAnalyzed";

    @GetMapping("/compare")
    @SuppressWarnings("unchecked")
    public String form(@AuthenticationPrincipal UserDetails principal, HttpSession session,
                        @RequestParam(required = false) List<String> rivals,
                        @RequestParam(required = false) String analyzeError,
                        @RequestParam(required = false) String analyzed,
                        Model model) {
        Optional<String> mineId = activeStoreResolver.resolve(principal, session);
        if (mineId.isEmpty()) {
            return "redirect:/biz-auth?needStore=1";
        }

        // URL에 rivals 파라미터가 있으면 그걸 우선 사용(분석 직후 리다이렉트 등),
        // 없으면 세션에 저장된 마지막 비교 상태를 복원한다.
        List<String> effectiveRivals;
        boolean showResults;
        if (rivals != null) {
            effectiveRivals = rivals;
            showResults = "1".equals(analyzed);
        } else {
            effectiveRivals = (List<String>) session.getAttribute(SESSION_RIVALS);
            showResults = Boolean.TRUE.equals(session.getAttribute(SESSION_ANALYZED));
        }

        List<String> rivalIds = cleanRivals(mineId.get(), effectiveRivals);

        // 결과가 보이는 상태라면 세션에도 반영해 다음 방문 때 복원되도록 한다.
        if (showResults) {
            session.setAttribute(SESSION_RIVALS, rivalIds);
            session.setAttribute(SESSION_ANALYZED, Boolean.TRUE);
        }

        model.addAttribute("stores", compareService.allStores().stream()
                .filter(s -> !s.getStoreId().equals(mineId.get()))
                .toList());
        model.addAttribute("selectedMine", mineId.get());
        model.addAttribute("selectedRivals", rivalIds);
        model.addAttribute("analyzeError", analyzeError);
        // "비교하기"를 눌러 분석이 한 번 실행된 뒤에만 종합 평가 이하 결과를 보여준다.
        model.addAttribute("showResults", showResults);

        model.addAttribute("result", compareService.compare(mineId.get(), rivalIds));
        model.addAttribute("insight", insightService.getCached(mineId.get()).orElse(null));
        model.addAttribute("insightItems", insightService.getCachedItems(mineId.get()));
        model.addAttribute("insightKeypoints", insightService.getCachedKeypoints(mineId.get()));
        model.addAttribute("comparisonsByRival", insightService.getCachedComparisons(mineId.get(), rivalIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.cafe.dashboard.entity.ReviewInsightComparison::getRivalStoreId, c -> c)));
        model.addAttribute("wordCloud", wordCloudService.compute(mineId.get()));
        model.addAttribute("kakaoMapKey", kakaoMapKey);
        return "compare";
    }

    @PostMapping("/compare/analyze")
    public String analyze(@AuthenticationPrincipal UserDetails principal, HttpSession session,
                           @RequestParam(required = false) List<String> rivals) {
        String mine = activeStoreResolver.resolve(principal, session)
                .orElseThrow(() -> new IllegalStateException("연결된 매장이 없습니다."));
        List<String> rivalIds = cleanRivals(mine, rivals);
        // 마지막 비교 상태를 세션에 저장 (로그아웃 전까지 유지, 다른 탭 다녀와도 복원)
        session.setAttribute(SESSION_RIVALS, rivalIds);
        session.setAttribute(SESSION_ANALYZED, Boolean.TRUE);
        UriComponentsBuilder redirect = UriComponentsBuilder.fromPath("/compare");
        rivalIds.forEach(r -> redirect.queryParam("rivals", r));
        // 분석 성공/실패와 무관하게 결과 영역은 펼쳐 보여준다.
        redirect.queryParam("analyzed", "1");
        try {
            insightService.analyze(mine, rivalIds);
        } catch (Exception e) {
            String message = e.getMessage() == null ? "알 수 없는 오류" : e.getMessage();
            redirect.queryParam("analyzeError", message.length() > 200 ? message.substring(0, 200) : message);
        }
        return "redirect:" + redirect.encode().build().toUriString();
    }

    private List<String> cleanRivals(String mineId, List<String> rivals) {
        return (rivals == null ? List.<String>of() : rivals).stream()
                .filter(s -> s != null && !s.isBlank() && !s.equals(mineId))
                .distinct()
                .limit(3)
                .toList();
    }
}
