package com.ecommerce.productordermanagement.service.impl;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.ecommerce.productordermanagement.entity.Product;
import com.ecommerce.productordermanagement.exception.ResourceNotFoundException;
import com.ecommerce.productordermanagement.repository.ProductRepository;
import com.ecommerce.productordermanagement.service.ProductService;

@Service
public class ProductServiceImpl implements ProductService, InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductServiceImpl.class);
    private static final Pattern SEARCH_TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]{3,}");

    private final ProductRepository productRepository;
    @Value("${spring.datasource.driver-class-name:}")
    private String databaseDriverClassName = "";
    private boolean mysqlFullTextIndexAvailable;

    private final ConcurrentHashMap<String, List<String>> categoryCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, List<Product>> featuredCache = new ConcurrentHashMap<>();

    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    private void clearCaches() {
        categoryCache.clear();
        featuredCache.clear();
    }

    @Override
    public void afterPropertiesSet() {
        if (isMySql()) {
            mysqlFullTextIndexAvailable = productRepository.hasCatalogFullTextIndex();
            if (!mysqlFullTextIndexAvailable) {
                LOGGER.warn("MySQL catalog full-text index is missing; product searches will use the slower LIKE query.");
            }
        } else if (!hasH2ProductTextIndex()) {
            LOGGER.warn("H2 catalog full-text index is missing; product searches will use the slower fallback query. "
                    + "Apply H2__product_catalog_search_indexes.sql while the local application is stopped.");
        }
    }

    @Override
    public Product saveProduct(Product product) {
        product.setImageUrl(normalizeImageUrl(product.getImageUrl()));
        Product saved = productRepository.save(product);
        clearCaches();
        return saved;
    }

    @Override
    public List<Product> getFeaturedProducts(int size) {
        int pageSize = Math.min(Math.max(size, 1), 20);
        return featuredCache.computeIfAbsent(pageSize, s ->
                productRepository.findFeaturedWithImage(
                        PageRequest.of(0, s, Sort.by(Sort.Direction.DESC, "id"))));
    }

    @Override
    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found with id: " + id
                        )
                );
    }

    @Override
    public Product updateProduct(Long id, Product product) {
        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found with id: " + id
                        )
                );

        existingProduct.setName(product.getName());
        existingProduct.setDescription(product.getDescription());
        existingProduct.setPrice(product.getPrice());
        existingProduct.setStock(product.getStock());
        existingProduct.setCategory(product.getCategory());
        existingProduct.setActualPrice(product.getActualPrice());
        if (product.getMainCategory() != null) {
            existingProduct.setMainCategory(product.getMainCategory());
        }
        if (product.getSubCategory() != null) {
            existingProduct.setSubCategory(product.getSubCategory());
        }
        existingProduct.setImageUrl(normalizeImageUrl(product.getImageUrl()));
        if (product.getProductUrl() != null) {
            existingProduct.setProductUrl(product.getProductUrl());
        }
        if (product.getRating() != null) {
            existingProduct.setRating(product.getRating());
        }
        if (product.getNumberOfRatings() != null) {
            existingProduct.setNumberOfRatings(product.getNumberOfRatings());
        }

        Product saved = productRepository.save(existingProduct);
        clearCaches();
        return saved;
    }

    @Override
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException(
                    "Product not found with id: " + id
            );
        }

        productRepository.deleteById(id);
        clearCaches();
    }

    @Override
    public Page<Product> getProductsWithPaginationAndSorting(
            int page,
            int size,
            String sortBy,
            String direction) {

        Pageable pageable = createPageable(page, size, sortBy, direction);
        return productRepository.findAll(pageable);
    }

    @Override
    public Page<Product> searchProducts(String search, String category,
                                        int page, int size, String sortBy,
                                        String direction) {
        return searchProducts(search, category, "", page, size, sortBy, direction);
    }

    @Override
    public Page<Product> searchProducts(String search, String category, String gender,
                                        int page, int size, String sortBy,
                                        String direction) {
        return searchProducts(search, category, "", "", gender, page, size, sortBy, direction);
    }

    @Override
    public Page<Product> searchProducts(String search, String category, String mainCategory,
                                        String subCategory, String gender, int page, int size,
                                        String sortBy, String direction) {
        Pageable pageable = createPageable(page, size, sortBy, direction);
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String normalizedCategory = category == null ? "" : category.trim();
        String normalizedMainCategory = mainCategory == null ? "" : mainCategory.trim();
        String normalizedSubCategory = subCategory == null ? "" : subCategory.trim();
        String categorySearchKey = normalizedCategory.toLowerCase(Locale.ROOT);
        String mainCategorySearchKey = normalizedMainCategory.toLowerCase(Locale.ROOT);
        String subCategorySearchKey = normalizedSubCategory.toLowerCase(Locale.ROOT);
        String normalizedGender = gender == null ? "" : gender.trim().toLowerCase(Locale.ROOT);
        if (!normalizedGender.isEmpty()
                && !List.of("women", "men", "kids").contains(normalizedGender)) {
            throw new IllegalArgumentException("Fashion gender must be women, men, or kids.");
        }
        if (normalizedSearch.isEmpty() && normalizedCategory.isEmpty()
                && normalizedMainCategory.isEmpty() && normalizedSubCategory.isEmpty()
                && normalizedGender.isEmpty()) {
            return productRepository.findAllWithImage(pageable);
        }
        if (!isMySql() && normalizedGender.isEmpty()) {
            boolean hasCategoryKeys = productRepository.hasH2CategorySearchKeys();
            boolean hasCatalogSearch = productRepository.hasH2CatalogSearchSupport();
            boolean hasTextIndex = hasCatalogSearch && productRepository.hasH2ProductTextIndex();

            if (normalizedSearch.isEmpty() && (hasCatalogSearch || hasCategoryKeys)) {
                if (!categorySearchKey.isEmpty()) {
                    return productRepository.filterProductsH2ByCategory(
                            productRepository.categoryValues(categorySearchKey),
                            mainCategorySearchKey,
                            subCategorySearchKey,
                            pageable);
                }
                if (!mainCategorySearchKey.isEmpty()) {
                    return productRepository.filterProductsH2ByMainCategory(
                            mainCategorySearchKey, subCategorySearchKey, pageable);
                }
                return productRepository.filterProductsH2BySubCategory(subCategorySearchKey, pageable);
            }

            if (!normalizedSearch.isEmpty() && hasTextIndex) {
                String searchPattern = "(?<![\\p{L}\\p{N}])" + Pattern.quote(normalizedSearch)
                        + "(?![\\p{L}\\p{N}])";
                boolean prioritizeName = "id".equalsIgnoreCase(sortBy)
                        && "asc".equalsIgnoreCase(direction);
                String normalizedSortBy = sortBy == null ? "id" : sortBy.trim().toLowerCase(Locale.ROOT);
                String normalizedDirection = direction == null ? "asc" : direction.trim().toLowerCase(Locale.ROOT);
                return productRepository.searchProductsH2FullText(
                        normalizedSearch,
                        searchPattern,
                        categorySearchKey,
                        productRepository.categoryValues(categorySearchKey),
                        mainCategorySearchKey,
                        subCategorySearchKey,
                        prioritizeName,
                        normalizedSortBy,
                        normalizedDirection,
                        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
            }
        }
        if (isMySql() && normalizedSearch.isEmpty() && normalizedMainCategory.isEmpty()
                && normalizedSubCategory.isEmpty() && !normalizedCategory.isEmpty()
                && normalizedGender.isEmpty()) {
            return productRepository.findByCatalogCategoryMySql(
                    mysqlCategoryValues(normalizedCategory),
                    pageable);
        }
        String fullTextSearch = fullTextSearchTerms(normalizedSearch, normalizedGender);
        if (isMySql() && mysqlFullTextIndexAvailable && !fullTextSearch.isEmpty()) {
            return productRepository.searchProductsFullText(
                    fullTextSearch, normalizedCategory, mysqlCategoryValues(normalizedCategory),
                    normalizedMainCategory, normalizedSubCategory, normalizedGender, pageable);
        }
        if (!isMySql() && !normalizedSearch.isEmpty() && normalizedGender.isEmpty()
                && "id".equalsIgnoreCase(sortBy) && "asc".equalsIgnoreCase(direction)) {
            return productRepository.searchProductsByRelevance(
                    normalizedSearch, normalizedCategory, normalizedMainCategory, normalizedSubCategory,
                    PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
        }
        return productRepository.searchProducts(
                normalizedSearch, normalizedCategory, normalizedMainCategory,
                normalizedSubCategory, normalizedGender, pageable);
    }

    @Override
    public List<String> getCategories() {
        return categoryCache.computeIfAbsent("categories", k -> {
            try {
                List<String> list = productRepository.findDistinctCategories();
                if (list != null && !list.isEmpty()) {
                    return list;
                }
            } catch (Exception ex) {
                LOGGER.warn("Could not query distinct categories from database, using cached fallback: {}", ex.getMessage());
            }
            return List.of("Electronics", "Accessories", "Fashion", "Appliances", "Home & Kitchen",
                    "Car & Motorbike", "Sports Fitness & Outdoors", "Books", "Beauty & Grooming",
                    "Toys & Games", "Pet Supplies", "Grocery & Gourmet Foods");
        });
    }

    @Override
    public List<String> getSubCategories() {
        return categoryCache.computeIfAbsent("subcategories", k -> {
            try {
                List<String> list = productRepository.findDistinctSubCategories();
                if (list != null && !list.isEmpty()) {
                    return list;
                }
            } catch (Exception ex) {
                LOGGER.warn("Could not query distinct subcategories from database, using cached fallback: {}", ex.getMessage());
            }
            return List.of("Cameras", "Headphones", "Speakers", "Televisions", "Casual Shoes",
                    "T-shirts and Polos", "Watches", "Car Accessories", "All Appliances");
        });
    }

    private Pageable createPageable(int page, int size, String sortBy, String direction) {
        String normalizedSort = sortBy == null ? "id" : sortBy.trim().toLowerCase(Locale.ROOT);
        if (!List.of("id", "name", "price").contains(normalizedSort)) {
            throw new IllegalArgumentException("Products can be sorted by id, name, or price.");
        }
        String normalizedDirection = direction == null ? "asc" : direction.trim().toLowerCase(Locale.ROOT);
        if (!List.of("asc", "desc").contains(normalizedDirection)) {
            throw new IllegalArgumentException("Sort direction must be asc or desc.");
        }
        Sort.Direction sortDirection = "desc".equals(normalizedDirection)
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(sortDirection, normalizedSort);
        if (!"id".equals(normalizedSort)) {
            sort = sort.and(Sort.by(sortDirection, "id"));
        }
        return PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), sort);
    }

    private boolean isMySql() {
        return databaseDriverClassName != null
                && databaseDriverClassName.startsWith("com.mysql.");
    }

    private boolean hasH2ProductTextIndex() {
        return productRepository.hasH2CatalogSearchSupport()
                && productRepository.hasH2ProductTextIndex();
    }

    private List<String> mysqlCategoryValues(String category) {
        String normalizedCategory = category.toLowerCase(Locale.ROOT);
        List<String> values = productRepository.categoryValues(normalizedCategory).stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (!category.isBlank() && values.stream().noneMatch(value -> value.equals(normalizedCategory))) {
            return java.util.stream.Stream.concat(values.stream(), java.util.stream.Stream.of(normalizedCategory))
                    .toList();
        }
        return values;
    }

    private String fullTextSearchTerms(String search, String gender) {
        String searchTerms = SEARCH_TOKEN_PATTERN.matcher(search.toLowerCase(Locale.ROOT))
                .results()
                .map(match -> "+" + match.group() + "*")
                .distinct()
                .collect(java.util.stream.Collectors.joining(" "));
        if (!searchTerms.isEmpty()) {
            return searchTerms;
        }
        return switch (gender) {
            case "women" -> "women* saree* kurti* dress* jeans* sandals* handbag* watch*";
            case "men" -> "men* shirt* trouser* jeans* wallet* belt* watch*";
            case "kids" -> "kids* kid* boy* girl*";
            default -> "";
        };
    }

    private String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        String normalized = imageUrl.trim();
        return normalized.matches("(?i)^https?://\\S+$") ? normalized : null;
    }
}
