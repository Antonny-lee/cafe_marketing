package com.cafe.dashboard.controller;

import com.cafe.dashboard.service.ActiveStoreResolver;
import com.cafe.dashboard.service.MarketingChatService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class MarketingChatController {

    private final MarketingChatService marketingChatService;
    private final ActiveStoreResolver activeStoreResolver;

    public record AskRequest(String message, List<MarketingChatService.ChatTurn> history) {}

    public record AskResponse(String reply) {}

    @GetMapping("/marketing-chat")
    public String page(@AuthenticationPrincipal UserDetails principal, HttpSession session, Model model) {
        Optional<String> storeId = activeStoreResolver.resolve(principal, session);
        if (storeId.isEmpty()) {
            return "redirect:/biz-auth?needStore=1";
        }
        return "marketing-chat";
    }

    @PostMapping("/marketing-chat/ask")
    @ResponseBody
    public AskResponse ask(@AuthenticationPrincipal UserDetails principal, HttpSession session,
                            @RequestBody AskRequest request) {
        String storeId = activeStoreResolver.resolve(principal, session)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "연결된 매장이 없습니다."));
        List<MarketingChatService.ChatTurn> history = request.history() == null ? List.of() : request.history();
        String reply = marketingChatService.ask(storeId, history, request.message());
        return new AskResponse(reply);
    }
}
