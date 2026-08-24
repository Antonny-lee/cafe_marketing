package com.cafe.dashboard.controller;

import com.cafe.dashboard.service.StoreService;
import com.cafe.dashboard.service.WordCloudService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;
    private final WordCloudService wordCloudService;

    @GetMapping("/stores")
    public String list(@RequestParam(required = false) String keyword, Model model) {
        model.addAttribute("stores", storeService.search(keyword));
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        return "stores";
    }

    @GetMapping("/stores/{storeId}")
    public String detail(@PathVariable String storeId,
                          @RequestParam(defaultValue = "0") int page,
                          Model model) {
        model.addAttribute("detail", storeService.loadDetail(storeId, page));
        model.addAttribute("wordCloud", wordCloudService.compute(storeId));
        return "store-detail";
    }

    @GetMapping("/stores/{storeId}/reviews")
    public String reviews(@PathVariable String storeId,
                           @RequestParam(defaultValue = "0") int page,
                           Model model) {
        model.addAttribute("data", storeService.loadReviews(storeId, page));
        return "store-reviews";
    }
}
