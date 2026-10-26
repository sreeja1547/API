package com.ecommerce.productordermanagement.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.AccessDeniedException;

import com.ecommerce.productordermanagement.dto.SellerRegistrationRequest;
import com.ecommerce.productordermanagement.dto.SellerRegistrationResponse;
import com.ecommerce.productordermanagement.entity.User;
import com.ecommerce.productordermanagement.service.SellerRegistrationService;
import com.ecommerce.productordermanagement.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final SellerRegistrationService sellerRegistrationService;

    public AuthController(UserService userService, SellerRegistrationService sellerRegistrationService) {
        this.userService = userService;
        this.sellerRegistrationService = sellerRegistrationService;
    }

    @PostMapping("/register")
    public UserResponse register(@RequestBody User user) {
        return UserResponse.from(userService.registerUser(user));
    }

    @PostMapping("/seller/register")
    public SellerRegistrationResponse registerSeller(@Valid @RequestBody SellerRegistrationRequest request) {
        return sellerRegistrationService.register(request);
    }

    @PostMapping("/login")
    public UserResponse login(@RequestBody LoginRequest request,
                              HttpServletRequest servletRequest) {
        User user = userService.loginUser(
                request.getEmail(),
                request.getPassword()
        );
        if (!"CUSTOMER".equalsIgnoreCase(user.getRole())) {
            throw new AccessDeniedException("Customer access required.");
        }
        return establishSession(user, servletRequest);
        }

        @PostMapping("/admin/login")
        public UserResponse adminLogin(@RequestBody LoginRequest request,
                       HttpServletRequest servletRequest) {
        User user = userService.loginUser(request.getEmail(), request.getPassword());
            if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
                throw new AccessDeniedException("Admin access required.");
        }
        return establishSession(user, servletRequest);
        }

        @PostMapping("/seller/login")
        public UserResponse sellerLogin(@RequestBody LoginRequest request,
                                        HttpServletRequest servletRequest) {
            User user = userService.loginUser(request.getEmail(), request.getPassword());
            if (!"SELLER".equalsIgnoreCase(user.getRole())) {
                throw new AccessDeniedException("Seller access required.");
            }
            return establishSession(user, servletRequest);
        }

        private UserResponse establishSession(User user, HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(true);
        servletRequest.changeSessionId();
        session.setAttribute("shopverseUserId", user.getId());
        return UserResponse.from(user);
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @GetMapping("/session")
    public UserResponse sessionUser(HttpSession session) {
        Object userId = session.getAttribute("shopverseUserId");
        if (!(userId instanceof Long id)) {
            throw new com.ecommerce.productordermanagement.exception.UnauthorizedException(
                    "Please sign in to continue.");
        }
        return UserResponse.from(userService.getUserById(id));
    }

    public record UserResponse(Long id, String name, String email,
                               String phone, String role) {
        private static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getName(),
                    user.getEmail(), user.getPhone(), user.getRole());
        }
    }

    public static class LoginRequest {

        private String email;
        private String password;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}