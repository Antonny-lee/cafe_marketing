package com.cafe.dashboard.controller;

import com.cafe.dashboard.entity.AppUser;
import com.cafe.dashboard.repository.AppUserRepository;
import com.cafe.dashboard.service.BusinessVerificationService;
import com.cafe.dashboard.service.CompareService;
import com.cafe.dashboard.service.InsightService;
import com.cafe.dashboard.service.WordCloudService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final BusinessVerificationService businessVerificationService;
    private final AppUserRepository appUserRepository;
    private final WordCloudService wordCloudService;

    @GetMapping("/compare")
    public String form(@AuthenticationPrincipal UserDetails principal,
                        @RequestParam(required = false) List<String> rivals,
                        @RequestParam(required = false) String analyzeError,
                        Model model) {
        Optional<String> mineId = myStoreId(principal);
        if (mineId.isEmpty()) {
            return "redirect:/biz-auth?needStore=1";
        }

        List<String> rivalIds = cleanRivals(mineId.get(), rivals);
        model.addAttribute("stores", compareService.allStores().stream()
                .filter(s -> !s.getStoreId().equals(mineId.get()))
                .toList());
        model.addAttribute("selectedMine", mineId.get());
        model.addAttribute("selectedRivals", rivalIds);
        model.addAttribute("analyzeError", analyzeError);

        model.addAttribute("result", compareService.compare(mineId.get(), rivalIds));
        model.addAttribute("insight", insightService.getCached(mineId.get()).orElse(null));
        model.addAttribute("insightItems", insightService.getCachedItems(mineId.get()));
        model.addAttribute("comparisonsByRival", insightService.getCachedComparisons(mineId.get(), rivalIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.cafe.dashboard.entity.ReviewInsightComparison::getRivalStoreId, c -> c)));
        model.addAttribute("wordCloud", wordCloudService.compute(mineId.get()));
        return "compare";
    }

    @PostMapping("/compare/analyze")
    public String analyze(@AuthenticationPrincipal UserDetails principal,
                           @RequestParam(required = false) List<String> rivals) {
        String mine = myStoreId(principal).orElseThrow(() -> new IllegalStateException("연결된 매장이 없습니다."));
        List<String> rivalIds = cleanRivals(mine, rivals);
        UriComponentsBuilder redirect = UriComponentsBuilder.fromPath("/compare");
        rivalIds.forEach(r -> redirect.queryParam("rivals", r));
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

    private Optional<String> myStoreId(UserDetails principal) {
        if (principal == null) return Optional.empty();
        AppUser user = appUserRepository.findByEmail(principal.getUsername()).orElse(null);
        if (user == null) return Optional.empty();
        return businessVerificationService.getMyStoreId(user.getUserId());
    }
}
