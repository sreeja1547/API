package com.ecommerce.productordermanagement.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.productordermanagement.dto.SellerRegistrationRequest;
import com.ecommerce.productordermanagement.dto.SellerRegistrationResponse;
import com.ecommerce.productordermanagement.entity.Shop;
import com.ecommerce.productordermanagement.entity.User;
import com.ecommerce.productordermanagement.repository.ShopRepository;
import com.ecommerce.productordermanagement.repository.UserRepository;

@Service
public class SellerRegistrationService {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final PasswordEncoder passwordEncoder;

    public SellerRegistrationService(UserRepository userRepository, ShopRepository shopRepository,
                                    PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public SellerRegistrationResponse register(SellerRegistrationRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String gstNumber = request.getGstNumber().trim().toUpperCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email already registered.");
        }
        if (shopRepository.existsByGstNumberIgnoreCase(gstNumber)) {
            throw new IllegalArgumentException("GST number already registered.");
        }

        User seller = new User(request.getFullName().trim(), email, request.getPhone().trim(),
                passwordEncoder.encode(request.getPassword()), "SELLER");
        seller = userRepository.saveAndFlush(seller);

        Shop shop = new Shop();
        shop.setSeller(seller);
        shop.setShopName(request.getShopName().trim());
        shop.setDescription(request.getDescription().trim());
        shop.setFullAddress(request.getFullAddress().trim());
        shop.setCity(request.getCity().trim());
        shop.setState(request.getState().trim());
        shop.setPinCode(request.getPinCode().trim());
        shop.setGstNumber(gstNumber);
        shop.setCategories(request.getCategories().stream().map(String::trim).distinct().toList());
        shop = shopRepository.saveAndFlush(shop);

        return new SellerRegistrationResponse(seller.getId(), shop.getId(), seller.getEmail(), seller.getRole());
    }
}