package com.ecommerce.productordermanagement.controller;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.productordermanagement.entity.Promotion;
import com.ecommerce.productordermanagement.entity.User;
import com.ecommerce.productordermanagement.service.PromotionService;
import com.ecommerce.productordermanagement.service.SessionUserService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/admin/promotions")
public class PromotionController {

    private final PromotionService promotionService;
    private final SessionUserService sessionUserService;

    public PromotionController(PromotionService promotionService, SessionUserService sessionUserService) {
        this.promotionService = promotionService;
        this.sessionUserService = sessionUserService;
    }

    @GetMapping
    public List<Promotion> findAll(HttpSession session) {
        requireAdmin(session);
        return promotionService.findAll();
    }

    @PostMapping
    public Promotion create(@RequestBody PromotionService.PromotionRequest request, HttpSession session) {
        requireAdmin(session);
        return promotionService.create(request);
    }

    @PutMapping("/{id}")
    public Promotion update(@PathVariable Long id,
                            @RequestBody PromotionService.PromotionRequest request,
                            HttpSession session) {
        requireAdmin(session);
        return promotionService.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public Promotion setActive(@PathVariable Long id, @RequestBody ActivationRequest request,
                               HttpSession session) {
        requireAdmin(session);
        if (request == null) throw new IllegalArgumentException("Active state is required.");
        return promotionService.setActive(id, request.active());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, HttpSession session) {
        requireAdmin(session);
        promotionService.delete(id);
    }

    private void requireAdmin(HttpSession session) {
        User user = sessionUserService.requireUser(session);
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new AccessDeniedException("Admin access required.");
        }
    }

    public record ActivationRequest(boolean active) {
    }
}