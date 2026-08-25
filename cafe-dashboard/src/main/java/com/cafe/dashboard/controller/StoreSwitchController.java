package com.cafe.dashboard.controller;

import com.cafe.dashboard.entity.AppUser;
import com.cafe.dashboard.entity.Business;
import com.cafe.dashboard.entity.Store;
import com.cafe.dashboard.repository.AppUserRepository;
import com.cafe.dashboard.repository.StoreRepository;
import com.cafe.dashboard.service.ActiveStoreResolver;
import com.cafe.dashboard.service.BusinessVerificationService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class StoreSwitchController {

    private final AppUserRepository appUserRepository;
    private final BusinessVerificationService businessVerificationService;
    private final StoreRepository storeRepository;
    private final ActiveStoreResolver activeStoreResolver;

    @GetMapping("/switch-store")
    public String page(@AuthenticationPrincipal UserDetails principal, HttpSession session, Model model) {
        AppUser user = currentUser(principal);
        List<Business> businesses = businessVerificationService.getLinkedBusinesses(user.getUserId());
        Map<String, String> storeNames = storeRepository.findAllById(businesses.stream().map(Business::getStoreId).toList())
                .stream()
                .collect(Collectors.toMap(Store::getStoreId, Store::getName));

        model.addAttribute("businesses", businesses);
        model.addAttribute("storeNames", storeNames);
        model.addAttribute("activeStoreId", activeStoreResolver.resolve(principal, session).orElse(null));
        return "switch-store";
    }

    @PostMapping("/switch-store")
    public String select(@AuthenticationPrincipal UserDetails principal, HttpSession session,
                          @RequestParam String storeId) {
        AppUser user = currentUser(principal);
        if (businessVerificationService.ownsStore(user.getUserId(), storeId)) {
            session.setAttribute(ActiveStoreResolver.SESSION_KEY, storeId);
        }
        return "redirect:/";
    }

    private AppUser currentUser(UserDetails principal) {
        return appUserRepository.findByEmail(principal.getUsername()).orElseThrow();
    }
}
