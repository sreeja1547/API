package com.ecommerce.productordermanagement.service;

import java.util.List;

import org.springframework.data.domain.Page;

import com.ecommerce.productordermanagement.entity.Product;

public interface ProductService {

    Product saveProduct(Product product);

    List<Product> getFeaturedProducts(int size);

    Product getProductById(Long id);

    Product updateProduct(Long id, Product product);

    void deleteProduct(Long id);

    Page<Product> getProductsWithPaginationAndSorting(
            int page,
            int size,
            String sortBy,
            String direction);

    Page<Product> searchProducts(String search, String category, int page,
                                 int size, String sortBy, String direction);

    Page<Product> searchProducts(String search, String category, String gender,
                                 int page, int size, String sortBy, String direction);

    Page<Product> searchProducts(String search, String category, String mainCategory,
                                 String subCategory, String gender, int page, int size,
                                 String sortBy, String direction);

    List<String> getCategories();

    List<String> getSubCategories();
}