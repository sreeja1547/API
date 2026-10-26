package com.ecommerce.productordermanagement.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.productordermanagement.dto.ProductCardResponse;
import com.ecommerce.productordermanagement.dto.ProductComparisonInfo;
import com.ecommerce.productordermanagement.entity.Product;
import com.ecommerce.productordermanagement.entity.User;
import com.ecommerce.productordermanagement.service.ProductService;
import com.ecommerce.productordermanagement.service.PromotionService;
import com.ecommerce.productordermanagement.service.SessionUserService;
import com.ecommerce.productordermanagement.service.impl.ProductComparisonService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private final SessionUserService sessionUserService;
    private final PromotionService promotionService;
    private final ProductComparisonService productComparisonService;

    public ProductController(ProductService productService, SessionUserService sessionUserService,
                             PromotionService promotionService) {
        this(productService, sessionUserService, promotionService, new ProductComparisonService());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ProductController(ProductService productService, SessionUserService sessionUserService,
                             PromotionService promotionService, ProductComparisonService productComparisonService) {
        this.productService = productService;
        this.sessionUserService = sessionUserService;
        this.promotionService = promotionService;
        this.productComparisonService = productComparisonService;
    }

    @PostMapping
    public Product saveProduct(@Valid @RequestBody Product product, HttpSession session) {
        requireAdmin(session);
        return productService.saveProduct(product);
    }

    @GetMapping
    public List<ProductCardResponse> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Product> products = productService.getProductsWithPaginationAndSorting(page, size, "id", "asc");
        promotionService.decorateProducts(products.getContent());
        productComparisonService.decorateWithComparison(products.getContent());
        return products.map(ProductCardResponse::from).getContent();
    }

    @GetMapping("/featured")
    public List<Product> getFeaturedProducts(@RequestParam(defaultValue = "8") int size) {
        List<Product> products = productService.getFeaturedProducts(size);
        promotionService.decorateProducts(products);
        productComparisonService.decorateWithComparison(products);
        return products;
    }

    @GetMapping("/page")
    public Page<ProductCardResponse> getProductsWithPaginationAndSorting(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String mainCategory,
            @RequestParam(defaultValue = "") String subCategory,
            @RequestParam(defaultValue = "") String gender) {

        Page<Product> products = productService.searchProducts(search, category, mainCategory,
                subCategory, gender, page, size, sortBy, direction);
        promotionService.decorateProducts(products.getContent());
        productComparisonService.decorateWithComparison(products.getContent());
        return products.map(ProductCardResponse::from);
    }

    @GetMapping("/filter")
    public Page<ProductCardResponse> filterProducts(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String mainCategory,
            @RequestParam(defaultValue = "") String subCategory,
            @RequestParam(defaultValue = "") String gender,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        Page<Product> products = productService.searchProducts(
            search, category, mainCategory, subCategory, gender, page, size, sortBy, direction);
        promotionService.decorateProducts(products.getContent());
        productComparisonService.decorateWithComparison(products.getContent());
        return products.map(ProductCardResponse::from);
    }

    @GetMapping("/categories")
    public List<String> getCategories() {
        return productService.getCategories();
    }

    @GetMapping("/subcategories")
    public List<String> getSubCategories() {
        return productService.getSubCategories();
    }

    @GetMapping("/{id}")
    public Product getProductById(@PathVariable Long id) {
        Product product = productService.getProductById(id);
        promotionService.decorateProducts(List.of(product));
        productComparisonService.decorateWithComparison(product);
        return product;
    }

    @GetMapping("/{id}/comparison")
    public ProductComparisonInfo getProductComparison(@PathVariable Long id) {
        Product product = productService.getProductById(id);
        promotionService.decorateProducts(List.of(product));
        return productComparisonService.buildComparison(product);
    }

    @GetMapping("/compare")
    public List<ProductCardResponse> compareMultipleProducts(@RequestParam List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .distinct()
                .limit(6)
                .map(id -> {
                    try {
                        Product p = productService.getProductById(id);
                        promotionService.decorateProducts(List.of(p));
                        productComparisonService.decorateWithComparison(p);
                        return ProductCardResponse.from(p);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @PutMapping("/{id}")
    public Product updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody Product product,
            HttpSession session) {

        requireAdmin(session);
        return productService.updateProduct(id, product);
    }

    @DeleteMapping("/{id}")
    public String deleteProduct(@PathVariable Long id, HttpSession session) {
        requireAdmin(session);
        productService.deleteProduct(id);
        return "Product deleted successfully.";
    }

    private void requireAdmin(HttpSession session) {
        User user = sessionUserService.requireUser(session);
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new AccessDeniedException("Admin access required.");
        }
    }
}
