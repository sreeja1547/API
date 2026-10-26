package com.ecommerce.productordermanagement.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.productordermanagement.entity.StoreSetting;
import com.ecommerce.productordermanagement.repository.StoreSettingRepository;

@Service
public class StoreSettingsService {

    private static final long SETTINGS_ID = 1L;
    private static final String DEFAULT_DISPLAY_NAME = "ShopVerse";

    private final StoreSettingRepository storeSettingRepository;

    public StoreSettingsService(StoreSettingRepository storeSettingRepository) {
        this.storeSettingRepository = storeSettingRepository;
    }

    @Transactional(readOnly = true)
    public String getDisplayName() {
        return storeSettingRepository.findById(SETTINGS_ID)
                .map(StoreSetting::getDisplayName)
                .orElse(DEFAULT_DISPLAY_NAME);
    }

    @Transactional
    public String updateDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank() || displayName.trim().length() > 120) {
            throw new IllegalArgumentException("Store display name must contain 1 to 120 characters.");
        }
        StoreSetting setting = storeSettingRepository.findById(SETTINGS_ID).orElseGet(() -> {
            StoreSetting created = new StoreSetting();
            created.setId(SETTINGS_ID);
            return created;
        });
        setting.setDisplayName(displayName.trim());
        return storeSettingRepository.save(setting).getDisplayName();
    }
}