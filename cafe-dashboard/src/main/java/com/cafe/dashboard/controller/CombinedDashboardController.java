package com.cafe.dashboard.controller;

import com.cafe.dashboard.service.DashboardService;
import com.cafe.dashboard.service.MarketAreaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class CombinedDashboardController {

    private final DashboardService dashboardService;
    private final MarketAreaService marketAreaService;

    @GetMapping("/market")
    public String combined(Model model) {
        DashboardService.Overview overview = dashboardService.loadOverview();
        model.addAttribute("overview", overview);

        MarketAreaService.MarketAreaOverview marketOverview = marketAreaService.load();
        model.addAttribute("report", marketOverview.report());
        model.addAttribute("m", marketOverview.metricsByName());
        model.addAttribute("seriesByMetric", marketOverview.seriesByMetric());
        model.addAttribute("breakdownByCategory", marketOverview.breakdownByCategory());
        model.addAttribute("rankedByCategory", marketOverview.rankedByCategory());
        model.addAttribute("genderDonut", marketOverview.genderDonut());
        model.addAttribute("industryDonuts", marketOverview.industryDonuts());

        return "dashboard-market";
    }
}
