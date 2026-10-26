package com.ecommerce.productordermanagement.service;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.productordermanagement.dto.ShopProfileRequest;
import com.ecommerce.productordermanagement.dto.ShopResponse;
import com.ecommerce.productordermanagement.entity.Shop;
import com.ecommerce.productordermanagement.entity.User;
import com.ecommerce.productordermanagement.exception.ResourceNotFoundException;
import com.ecommerce.productordermanagement.repository.ShopRepository;

@Service
public class ShopService {

    private final ShopRepository shopRepository;

    public ShopService(ShopRepository shopRepository) {
        this.shopRepository = shopRepository;
    }

    @Transactional(readOnly = true)
    public ShopResponse getProfile(User seller) {
        return ShopResponse.from(requireShopForSeller(seller));
    }

    @Transactional
    public ShopResponse updateProfile(User seller, ShopProfileRequest request) {
        Shop shop = requireShopForSeller(seller);
        String gstNumber = request.gstNumber().trim().toUpperCase(Locale.ROOT);
        if (!shop.getGstNumber().equalsIgnoreCase(gstNumber)
                && shopRepository.existsByGstNumberIgnoreCaseAndIdNot(gstNumber, shop.getId())) {
            throw new IllegalArgumentException("GST number already registered.");
        }
        shop.setShopName(request.shopName().trim());
        shop.setDescription(request.description().trim());
        shop.setFullAddress(request.fullAddress().trim());
        shop.setCity(request.city().trim());
        shop.setState(request.state().trim());
        shop.setPinCode(request.pinCode().trim());
        shop.setGstNumber(gstNumber);
        shop.setCategories(request.categories().stream().map(String::trim).distinct().toList());
        return ShopResponse.from(shopRepository.save(shop));
    }

    @Transactional(readOnly = true)
    public Shop requireShopForSeller(User seller) {
        if (seller == null || seller.getId() == null) {
            throw new IllegalArgumentException("Seller is required.");
        }
        return shopRepository.findBySellerId(seller.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found."));
    }
}