package com.ecommerce.productordermanagement.controller;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.productordermanagement.entity.User;
import com.ecommerce.productordermanagement.service.SessionUserService;
import com.ecommerce.productordermanagement.service.StoreSettingsService;

import jakarta.servlet.http.HttpSession;

@RestController
public class StoreSettingsController {

    private final StoreSettingsService storeSettingsService;
    private final SessionUserService sessionUserService;

    public StoreSettingsController(StoreSettingsService storeSettingsService,
                                   SessionUserService sessionUserService) {
        this.storeSettingsService = storeSettingsService;
        this.sessionUserService = sessionUserService;
    }

    @GetMapping("/store/settings")
    public StoreNameResponse publicStoreName() {
        return new StoreNameResponse(storeSettingsService.getDisplayName());
    }

    @PutMapping("/admin/settings/store")
    public StoreNameResponse updateStoreName(@RequestBody StoreNameRequest request, HttpSession session) {
        User user = sessionUserService.requireUser(session);
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new AccessDeniedException("Admin access required.");
        }
        if (request == null) throw new IllegalArgumentException("Store display name is required.");
        return new StoreNameResponse(storeSettingsService.updateDisplayName(request.displayName()));
    }

    public record StoreNameRequest(String displayName) {
    }

    public record StoreNameResponse(String displayName) {
    }
}