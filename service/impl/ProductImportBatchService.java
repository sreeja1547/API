package com.ecommerce.productordermanagement.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.productordermanagement.entity.Product;
import com.ecommerce.productordermanagement.repository.ProductRepository;

import jakarta.persistence.EntityManager;

@Service
public class ProductImportBatchService {

    private final ProductRepository productRepository;
    private final EntityManager entityManager;

    public ProductImportBatchService(ProductRepository productRepository,
                                     EntityManager entityManager) {
        this.productRepository = productRepository;
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int saveBatch(List<Product> products) {
        productRepository.saveAll(products);
        productRepository.flush();
        entityManager.clear();
        return products.size();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveOne(Product product) {
        productRepository.saveAndFlush(product);
        entityManager.clear();
    }
}
