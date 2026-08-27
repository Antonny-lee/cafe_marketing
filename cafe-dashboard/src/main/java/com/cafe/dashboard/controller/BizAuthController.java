package com.cafe.dashboard.controller;

import com.cafe.dashboard.entity.AppUser;
import com.cafe.dashboard.repository.AppUserRepository;
import com.cafe.dashboard.repository.BusinessRepository;
import com.cafe.dashboard.repository.StoreRepository;
import com.cafe.dashboard.service.BusinessVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class BizAuthController {

    private final BusinessVerificationService verificationService;
    private final BusinessRepository businessRepository;
    private final AppUserRepository appUserRepository;
    private final StoreRepository storeRepository;

    @GetMapping("/biz-auth")
    public String form(@AuthenticationPrincipal UserDetails principal,
                        @RequestParam(required = false) String needStore,
                        @RequestParam(required = false) String manage,
                        Model model) {
        AppUser user = currentUser(principal);
        var businesses = businessRepository.findByOwnerUserId(user.getUserId());

        // 이미 사업자 인증 + 매장 연결까지 끝난 사용자는 등록 화면을 건너뛰고 홈으로 보낸다.
        // (?manage=1 로 접근하면 관리 목적으로 화면을 그대로 보여준다.)
        boolean hasLinkedStore = businesses.stream()
                .anyMatch(b -> b.getStoreId() != null && !b.getStoreId().isBlank());
        if (hasLinkedStore && manage == null) {
            return "redirect:/";
        }

        model.addAttribute("businesses", businesses);
        model.addAttribute("stores", storeRepository.findAll());
        model.addAttribute("needStore", needStore != null);
        return "biz-auth";
    }

    @PostMapping("/biz-auth/verify")
    public String verify(@AuthenticationPrincipal UserDetails principal,
                          @RequestParam String bizRegNo,
                          @RequestParam String openDate,
                          @RequestParam String ceoName,
                          @RequestParam(required = false) String bizName,
                          @RequestParam(required = false) String phone,
                          Model model) {
        AppUser user = currentUser(principal);
        BusinessVerificationService.VerifyResult result =
                verificationService.verify(bizRegNo, openDate, ceoName, bizName, phone, user.getUserId());
        if (result.valid()) {
            return "redirect:/";
        }
        model.addAttribute("result", result);
        model.addAttribute("businesses", businessRepository.findByOwnerUserId(user.getUserId()));
        model.addAttribute("stores", storeRepository.findAll());
        return "biz-auth";
    }

    @PostMapping("/biz-auth/link-store")
    public String linkStore(@AuthenticationPrincipal UserDetails principal,
                             @RequestParam String bizRegNo,
                             @RequestParam(required = false) String storeId) {
        AppUser user = currentUser(principal);
        verificationService.linkStore(bizRegNo, user.getUserId(), storeId);
        return "redirect:/biz-auth";
    }

    @PostMapping("/biz-auth/delete")
    public String delete(@AuthenticationPrincipal UserDetails principal, @RequestParam String bizRegNo) {
        AppUser user = currentUser(principal);
        verificationService.deleteBusiness(bizRegNo, user.getUserId());
        return "redirect:/biz-auth";
    }

    private AppUser currentUser(UserDetails principal) {
        return appUserRepository.findByEmail(principal.getUsername()).orElseThrow();
    }
}
