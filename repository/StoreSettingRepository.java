package com.ecommerce.productordermanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommerce.productordermanagement.entity.StoreSetting;

public interface StoreSettingRepository extends JpaRepository<StoreSetting, Long> {
}