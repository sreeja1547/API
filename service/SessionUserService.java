package com.ecommerce.productordermanagement.service;

import org.springframework.stereotype.Service;

import com.ecommerce.productordermanagement.entity.User;
import com.ecommerce.productordermanagement.exception.UnauthorizedException;
import com.ecommerce.productordermanagement.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

@Service
public class SessionUserService {

    public static final String USER_ID_ATTRIBUTE = "shopverseUserId";

    private final UserRepository userRepository;

    public SessionUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireUser(HttpSession session) {
        if (session == null) {
            throw new UnauthorizedException("Please sign in to continue.");
        }
        Object value = session.getAttribute(USER_ID_ATTRIBUTE);
        if (!(value instanceof Long userId)) {
            throw new UnauthorizedException("Please sign in to continue.");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Please sign in to continue."));
    }
}
