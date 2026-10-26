package com.ecommerce.productordermanagement.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.productordermanagement.dto.CsvCatalogProduct;
import com.ecommerce.productordermanagement.service.impl.CsvProductCatalogService;

@RestController
@RequestMapping("/catalog/csv/products")
public class CsvProductCatalogController {

    private final CsvProductCatalogService catalogService;

    public CsvProductCatalogController(CsvProductCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/page")
    public Page<CsvCatalogProduct> getPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String gender) {
        return catalogService.getPage(page, size, search, category, gender, sortBy, direction);
    }

    @GetMapping("/categories")
    public List<String> getCategories() {
        return catalogService.getCategories();
    }

    @GetMapping("/{id}")
    public CsvCatalogProduct getProduct(@PathVariable String id) {
        return catalogService.getById(id);
    }
}