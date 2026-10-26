package com.ecommerce.productordermanagement.controller;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.productordermanagement.dto.SellerOrderResponse;
import com.ecommerce.productordermanagement.dto.SellerProductRequest;
import com.ecommerce.productordermanagement.dto.SellerProductResponse;
import com.ecommerce.productordermanagement.dto.SellerStatsResponse;
import com.ecommerce.productordermanagement.dto.ShopProfileRequest;
import com.ecommerce.productordermanagement.dto.ShopResponse;
import com.ecommerce.productordermanagement.entity.User;
import com.ecommerce.productordermanagement.service.SellerManagementService;
import com.ecommerce.productordermanagement.service.SessionUserService;
import com.ecommerce.productordermanagement.service.ShopService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/seller/api")
public class SellerController {

    private final SessionUserService sessionUserService;
    private final ShopService shopService;
    private final SellerManagementService sellerManagementService;

    public SellerController(SessionUserService sessionUserService, ShopService shopService,
                            SellerManagementService sellerManagementService) {
        this.sessionUserService = sessionUserService;
        this.shopService = shopService;
        this.sellerManagementService = sellerManagementService;
    }

    @GetMapping("/shop")
    public ShopResponse getShop(HttpSession session) {
        return shopService.getProfile(requireSeller(session));
    }

    @PutMapping("/shop")
    public ShopResponse updateShop(@Valid @RequestBody ShopProfileRequest request, HttpSession session) {
        return shopService.updateProfile(requireSeller(session), request);
    }

    @GetMapping("/stats")
    public SellerStatsResponse getStats(HttpSession session) {
        return sellerManagementService.getStats(requireSeller(session));
    }

    @GetMapping("/products")
    public List<SellerProductResponse> getProducts(HttpSession session) {
        return sellerManagementService.getProducts(requireSeller(session));
    }

    @PostMapping("/products")
    public SellerProductResponse createProduct(@Valid @RequestBody SellerProductRequest request,
                                               HttpSession session) {
        return sellerManagementService.createProduct(requireSeller(session), request);
    }

    @PutMapping("/products/{id}")
    public SellerProductResponse updateProduct(@PathVariable Long id,
                                               @Valid @RequestBody SellerProductRequest request,
                                               HttpSession session) {
        return sellerManagementService.updateProduct(requireSeller(session), id, request);
    }

    @DeleteMapping("/products/{id}")
    public void deleteProduct(@PathVariable Long id, HttpSession session) {
        sellerManagementService.deleteProduct(requireSeller(session), id);
    }

    @GetMapping("/orders")
    public List<SellerOrderResponse> getOrders(HttpSession session) {
        return sellerManagementService.getOrders(requireSeller(session));
    }

    private User requireSeller(HttpSession session) {
        User user = sessionUserService.requireUser(session);
        if (!"SELLER".equalsIgnoreCase(user.getRole())) {
            throw new AccessDeniedException("Seller access required.");
        }
        return user;
    }
}