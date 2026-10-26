package com.ecommerce.productordermanagement.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommerce.productordermanagement.entity.Shop;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    Optional<Shop> findBySellerId(Long sellerId);

    boolean existsByGstNumberIgnoreCase(String gstNumber);

    boolean existsByGstNumberIgnoreCaseAndIdNot(String gstNumber, Long id);
}