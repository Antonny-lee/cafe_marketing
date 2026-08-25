package com.cafe.dashboard.controller;

import com.cafe.dashboard.repository.StoreRepository;
import com.cafe.dashboard.service.ActiveStoreResolver;
import com.cafe.dashboard.service.LedgerService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final LedgerService ledgerService;
    private final ActiveStoreResolver activeStoreResolver;
    private final StoreRepository storeRepository;

    @GetMapping("/")
    public String home(@AuthenticationPrincipal UserDetails principal, HttpSession session, Model model) {
        String storeId = activeStoreResolver.resolve(principal, session).orElse(null);
        if (storeId == null) {
            return "redirect:/biz-auth?needStore=1";
        }

        model.addAttribute("store", storeRepository.findById(storeId).orElseThrow());
        model.addAttribute("home", ledgerService.loadHomeSummary(storeId));
        model.addAttribute("todayLabel", LocalDate.now().format(
                DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)));
        return "home";
    }

    @GetMapping("/my-store")
    public String myStore(@AuthenticationPrincipal UserDetails principal, HttpSession session, Model model) {
        String storeId = activeStoreResolver.resolve(principal, session).orElse(null);
        if (storeId == null) {
            return "redirect:/biz-auth?needStore=1";
        }

        model.addAttribute("store", storeRepository.findById(storeId).orElseThrow());
        return "my-store";
    }
}
