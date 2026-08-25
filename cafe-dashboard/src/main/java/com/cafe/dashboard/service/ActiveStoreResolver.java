package com.cafe.dashboard.service;

import com.cafe.dashboard.entity.AppUser;
import com.cafe.dashboard.repository.AppUserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves which store the current user is currently viewing data for.
 * A user can have several verified businesses linked to different stores; the chosen one
 * is kept in the HTTP session (set via /switch-store) and falls back to their first linked
 * store if nothing is selected yet or the selection no longer belongs to them.
 */
@Component
@RequiredArgsConstructor
public class ActiveStoreResolver {

    public static final String SESSION_KEY = "activeStoreId";

    private final AppUserRepository appUserRepository;
    private final BusinessVerificationService businessVerificationService;

    public Optional<String> resolve(UserDetails principal, HttpSession session) {
        if (principal == null) return Optional.empty();
        AppUser user = appUserRepository.findByEmail(principal.getUsername()).orElse(null);
        if (user == null) return Optional.empty();

        Object selected = session.getAttribute(SESSION_KEY);
        if (selected instanceof String storeId && businessVerificationService.ownsStore(user.getUserId(), storeId)) {
            return Optional.of(storeId);
        }
        return businessVerificationService.getMyStoreId(user.getUserId());
    }
}
