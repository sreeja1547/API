package com.ecommerce.productordermanagement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.productordermanagement.entity.Product;
import com.ecommerce.productordermanagement.entity.Promotion;
import com.ecommerce.productordermanagement.exception.ResourceNotFoundException;
import com.ecommerce.productordermanagement.repository.ProductRepository;
import com.ecommerce.productordermanagement.repository.PromotionRepository;

@Service
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final ProductRepository productRepository;

    public PromotionService(PromotionRepository promotionRepository, ProductRepository productRepository) {
        this.promotionRepository = promotionRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<Promotion> findAll() {
        return promotionRepository.findAllByOrderByIdDesc();
    }

    @Transactional
    public Promotion create(PromotionRequest request) {
        validate(request);
        Promotion promotion = new Promotion();
        copy(request, promotion);
        return promotionRepository.save(promotion);
    }

    @Transactional
    public Promotion update(Long id, PromotionRequest request) {
        validate(request);
        Promotion promotion = findById(id);
        copy(request, promotion);
        return promotionRepository.save(promotion);
    }

    @Transactional
    public Promotion setActive(Long id, boolean active) {
        Promotion promotion = findById(id);
        promotion.setActive(active);
        return promotionRepository.save(promotion);
    }

    @Transactional
    public void delete(Long id) {
        promotionRepository.delete(findById(id));
    }

    @Transactional(readOnly = true)
    public void decorateProducts(Collection<Product> products) {
        if (products == null || products.isEmpty()) return;
        List<Promotion> active = promotionRepository.findActiveAt(LocalDateTime.now());
        for (Product product : products) {
            product.setPromotionalPrice(null);
            product.setPromotionName(null);
            effectivePromotion(product, active).ifPresent(promotion -> {
                BigDecimal currentPrice = BigDecimal.valueOf(product.getPrice());
                BigDecimal multiplier = BigDecimal.ONE.subtract(
                        promotion.getDiscountPercent().movePointLeft(2));
                product.setPromotionalPrice(currentPrice.multiply(multiplier)
                        .setScale(2, RoundingMode.HALF_UP).doubleValue());
                product.setPromotionName(promotion.getName());
            });
        }
    }

    @Transactional(readOnly = true)
    public double effectivePrice(Product product) {
        List<Promotion> active = promotionRepository.findActiveAt(LocalDateTime.now());
        return effectivePrice(product, active);
    }

    @Transactional(readOnly = true)
    public Map<Long, Double> effectivePrices(Collection<Product> products) {
        List<Promotion> active = promotionRepository.findActiveAt(LocalDateTime.now());
        Map<Long, Double> prices = new LinkedHashMap<>();
        for (Product product : products) {
            prices.put(product.getId(), effectivePrice(product, active));
        }
        return prices;
    }

    private java.util.Optional<Promotion> effectivePromotion(Product product, List<Promotion> promotions) {
        return promotions.stream()
                .filter(promotion -> promotion.getProductId() == null
                        || promotion.getProductId().equals(product.getId()))
                .max(Comparator.comparing(Promotion::getDiscountPercent));
    }

    private double effectivePrice(Product product, List<Promotion> active) {
        return effectivePromotion(product, active)
                .map(promotion -> BigDecimal.valueOf(product.getPrice())
                        .multiply(BigDecimal.ONE.subtract(promotion.getDiscountPercent().movePointLeft(2)))
                        .setScale(2, RoundingMode.HALF_UP).doubleValue())
                .orElse(product.getPrice());
    }

    private Promotion findById(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found."));
    }

    private void validate(PromotionRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Promotion name is required.");
        }
        if (request.description() == null || request.description().isBlank()) {
            throw new IllegalArgumentException("Promotion description is required.");
        }
        if (request.discountPercent() == null || request.discountPercent().signum() <= 0
                || request.discountPercent().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Discount must be greater than 0 and at most 100 percent.");
        }
        if (request.startsAt() != null && request.endsAt() != null
                && !request.endsAt().isAfter(request.startsAt())) {
            throw new IllegalArgumentException("Promotion end time must be after its start time.");
        }
        if (request.productId() != null && !productRepository.existsById(request.productId())) {
            throw new ResourceNotFoundException("Promotion product not found.");
        }
    }

    private void copy(PromotionRequest request, Promotion promotion) {
        promotion.setName(request.name().trim());
        promotion.setDescription(request.description().trim());
        promotion.setDiscountPercent(request.discountPercent().setScale(2, RoundingMode.HALF_UP));
        promotion.setProductId(request.productId());
        promotion.setStartsAt(request.startsAt());
        promotion.setEndsAt(request.endsAt());
        promotion.setActive(request.active());
    }

    public record PromotionRequest(String name, String description, BigDecimal discountPercent,
                                   Long productId, LocalDateTime startsAt, LocalDateTime endsAt,
                                   boolean active) {
    }
}