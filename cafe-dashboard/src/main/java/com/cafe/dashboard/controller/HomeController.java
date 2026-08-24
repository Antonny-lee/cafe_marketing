package com.cafe.dashboard.controller;

import com.cafe.dashboard.entity.AppUser;
import com.cafe.dashboard.repository.AppUserRepository;
import com.cafe.dashboard.repository.StoreRepository;
import com.cafe.dashboard.service.BusinessVerificationService;
import com.cafe.dashboard.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final LedgerService ledgerService;
    private final BusinessVerificationService businessVerificationService;
    private final AppUserRepository appUserRepository;
    private final StoreRepository storeRepository;

    @GetMapping("/")
    public String home(@AuthenticationPrincipal UserDetails principal, Model model) {
        String storeId = myStoreId(principal).orElse(null);
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
    public String myStore(@AuthenticationPrincipal UserDetails principal, Model model) {
        String storeId = myStoreId(principal).orElse(null);
        if (storeId == null) {
            return "redirect:/biz-auth?needStore=1";
        }

        model.addAttribute("store", storeRepository.findById(storeId).orElseThrow());
        return "my-store";
    }

    private Optional<String> myStoreId(UserDetails principal) {
        if (principal == null) return Optional.empty();
        AppUser user = appUserRepository.findByEmail(principal.getUsername()).orElse(null);
        if (user == null) return Optional.empty();
        return businessVerificationService.getMyStoreId(user.getUserId());
    }
}
