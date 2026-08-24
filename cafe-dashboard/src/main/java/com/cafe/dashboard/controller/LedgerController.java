package com.cafe.dashboard.controller;

import com.cafe.dashboard.entity.AppUser;
import com.cafe.dashboard.repository.AppUserRepository;
import com.cafe.dashboard.service.BusinessVerificationService;
import com.cafe.dashboard.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;
    private final BusinessVerificationService businessVerificationService;
    private final AppUserRepository appUserRepository;

    @GetMapping("/ledger")
    public String page(@AuthenticationPrincipal UserDetails principal,
                        @RequestParam(defaultValue = "status") String view,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) String error,
                        @RequestParam(required = false) String success,
                        Model model) {
        String storeId = myStoreId(principal).orElse(null);
        if (storeId == null) {
            return "redirect:/biz-auth?needStore=1";
        }

        model.addAttribute("view", view);
        model.addAttribute("overview", ledgerService.loadOverview(storeId));
        model.addAttribute("fixedCosts", ledgerService.listFixedCosts(storeId));
        model.addAttribute("expenses", ledgerService.listExpenses(storeId, page));
        model.addAttribute("monthlyComparison", ledgerService.monthlyComparison(storeId, 6));
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        model.addAttribute("dailySales", ledgerService.listDailySales(storeId, monthStart, LocalDate.now()));
        model.addAttribute("error", error);
        model.addAttribute("success", success);
        return "ledger";
    }

    @PostMapping("/ledger/sales")
    public String addSale(@AuthenticationPrincipal UserDetails principal,
                           @RequestParam String saleDate,
                           @RequestParam long amount) {
        String storeId = requireStoreId(principal);
        ledgerService.addManualSale(storeId, LocalDate.parse(saleDate), amount);
        return "redirect:/ledger?success=" + java.net.URLEncoder.encode("매출이 등록됐어요.", java.nio.charset.StandardCharsets.UTF_8);
    }

    @PostMapping("/ledger/sales/upload")
    public String uploadSales(@AuthenticationPrincipal UserDetails principal,
                               @RequestParam("file") MultipartFile file) {
        String storeId = requireStoreId(principal);
        UriComponentsBuilder redirect = UriComponentsBuilder.fromPath("/ledger").queryParam("view", "status");
        try {
            LedgerService.ImportResult result = ledgerService.importSalesFile(storeId, file);
            redirect.queryParam("success", "매출 리포트 반영 완료: " + result.rowsImported() + "건 (" +
                    result.from() + " ~ " + result.to() + ")");
        } catch (Exception e) {
            String message = e.getMessage() == null ? "업로드 처리 중 오류가 발생했습니다." : e.getMessage();
            redirect.queryParam("error", message);
        }
        return "redirect:" + redirect.encode().build().toUriString();
    }

    @PostMapping("/ledger/sales/{saleDate}/delete")
    public String deleteSale(@AuthenticationPrincipal UserDetails principal, @PathVariable String saleDate) {
        String storeId = requireStoreId(principal);
        ledgerService.deleteSale(storeId, LocalDate.parse(saleDate));
        return "redirect:/ledger?view=status&success=" +
                java.net.URLEncoder.encode(saleDate + " 매출 기록을 삭제했어요.", java.nio.charset.StandardCharsets.UTF_8);
    }

    @PostMapping("/ledger/expenses")
    public String addExpense(@AuthenticationPrincipal UserDetails principal,
                              @RequestParam String category,
                              @RequestParam(required = false) String vendor,
                              @RequestParam long amount,
                              @RequestParam String paymentMethod,
                              @RequestParam(required = false) String memo,
                              @RequestParam String expenseDate,
                              @RequestParam(defaultValue = "false") boolean recurring) {
        String storeId = requireStoreId(principal);
        ledgerService.addExpense(storeId, category, vendor, amount, paymentMethod, memo,
                LocalDate.parse(expenseDate), recurring);
        return "redirect:/ledger?view=register&success=" +
                java.net.URLEncoder.encode("지출이 기록됐어요.", java.nio.charset.StandardCharsets.UTF_8);
    }

    private String requireStoreId(UserDetails principal) {
        return myStoreId(principal).orElseThrow(() -> new IllegalStateException("연결된 매장이 없습니다."));
    }

    private Optional<String> myStoreId(UserDetails principal) {
        if (principal == null) return Optional.empty();
        AppUser user = appUserRepository.findByEmail(principal.getUsername()).orElse(null);
        if (user == null) return Optional.empty();
        return businessVerificationService.getMyStoreId(user.getUserId());
    }
}
