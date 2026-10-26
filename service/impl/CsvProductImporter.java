package com.ecommerce.productordermanagement.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ecommerce.productordermanagement.entity.Product;
import com.ecommerce.productordermanagement.repository.ProductRepository;

@Component
@ConditionalOnProperty(prefix = "app.dataset.import", name = "enabled",
    havingValue = "true")
public class CsvProductImporter implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(CsvProductImporter.class);
    private static final int BATCH_SIZE = 1000;
    private static final Pattern ASIN_PATTERN = Pattern.compile(
            "(?i)(?:/dp/|/gp/product/)([a-z0-9]{10})(?:[/?#]|$)");
    private static final Pattern AMAZON_IMAGE_RENDERING_PATH = Pattern.compile(
            "(?i)(https?://m\\.media-amazon\\.com)/images/W/IMAGERENDERING_[^/]+/images/I/");
    static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .setIgnoreHeaderCase(true)
            .setAllowMissingColumnNames(true)
            .get();

    private final ProductRepository productRepository;
    private final ProductImportBatchService batchService;

    @Value("${app.dataset.path:./dataset/amazon-dataset}")
    private String datasetPath;

    public CsvProductImporter(ProductRepository productRepository,
                              ProductImportBatchService batchService) {
        this.productRepository = productRepository;
        this.batchService = batchService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Path root = Paths.get(datasetPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            LOGGER.warn("Dataset directory {} does not exist; no products were imported.", root);
            return;
        }

        List<Path> files;
        try (var paths = Files.list(root)) {
            files = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
                    .sorted(Comparator.comparingInt(CsvProductImporter::masterFirst)
                            .thenComparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException exception) {
            LOGGER.error("Unable to list CSV files in {}. No products were imported.", root, exception);
            return;
        }

        if (files.isEmpty()) {
            LOGGER.warn("No CSV files found in {}; no products were imported.", root);
            return;
        }

        ImportStats stats = new ImportStats();
        Set<String> sourceIdentities = new HashSet<>();
        Set<String> countedExisting = new HashSet<>();
        Map<String, Candidate> pending = new LinkedHashMap<>();
        for (Path file : files) {
            stats.filesInspected++;
            boolean master = file.getFileName().toString().equalsIgnoreCase("Amazon-Products.csv");
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
                 CSVParser parser = CSVParser.parse(reader, CSV_FORMAT)) {
                for (CSVRecord record : parser) {
                    stats.rowsDiscovered++;
                    try {
                        if (!record.isConsistent()) {
                            stats.invalidRows++;
                            continue;
                        }
                        Candidate candidate = mapProduct(record, master);
                        if (candidate == null) {
                            stats.invalidRows++;
                            continue;
                        }
                        stats.rowsProcessed++;
                        if (!sourceIdentities.add(candidate.identity())) {
                            stats.duplicateRowsRemoved++;
                        }
                        Candidate previous = pending.get(candidate.identity());
                        if (previous == null || isBetter(candidate, previous)) {
                            pending.put(candidate.identity(), candidate);
                        }
                        if (pending.size() >= BATCH_SIZE) {
                            flushBatch(pending, countedExisting, stats);
                        }
                    } catch (RuntimeException exception) {
                        stats.invalidRows++;
                    }
                }
            } catch (IOException | RuntimeException exception) {
                stats.filesWithErrors++;
                LOGGER.error("Unable to fully parse dataset file {}; continuing with other files.", file, exception);
            }
            flushBatch(pending, countedExisting, stats);
        }
        flushBatch(pending, countedExisting, stats);
        stats.finalDatabaseProducts = productRepository.count();
        LOGGER.info("Dataset import complete: filesInspected={}, rowsDiscovered={}, rowsProcessed={}, "
                        + "duplicatesRemoved={}, invalidRowsSkipped={}, productsImported={}, "
                        + "existingProductsMatched={}, existingProductsUpdated={}, "
                        + "finalUniqueDatasetProducts={}, finalDatabaseProductCount={}, filesWithErrors={}",
                stats.filesInspected, stats.rowsDiscovered, stats.rowsProcessed,
                stats.duplicateRowsRemoved, stats.invalidRows, stats.productsImported,
                stats.existingProductsMatched, stats.existingProductsUpdated,
                sourceIdentities.size(), stats.finalDatabaseProducts, stats.filesWithErrors);
    }

    private void flushBatch(Map<String, Candidate> pending,
                            Set<String> countedExisting,
                            ImportStats stats) {
        if (pending.isEmpty()) {
            return;
        }

        Map<String, Product> storedByIdentity = new HashMap<>();
        productRepository.findAllBySourceIdentityIn(pending.keySet())
                .forEach(product -> storedByIdentity.put(product.getSourceIdentity(), product));
        List<String> legacyUrls = pending.values().stream()
                .map(candidate -> candidate.product().getProductUrl())
                .filter(url -> url != null && !url.isBlank())
                .distinct()
                .toList();
        if (!legacyUrls.isEmpty()) {
            productRepository.findAllByProductUrlIn(legacyUrls).forEach(product -> {
                String identity = identity(product.getSourceIdentity(), product.getProductUrl(),
                        product.getImageUrl(), product.getName());
                if (identity != null) {
                    storedByIdentity.putIfAbsent(identity, product);
                }
            });
        }
        List<Product> writes = new ArrayList<>();
        Set<String> newIdentities = new HashSet<>();
        Set<String> updateIdentities = new HashSet<>();
        for (Candidate candidate : pending.values()) {
            Product existing = storedByIdentity.get(candidate.identity());
            if (existing == null) {
                candidate.product().setSourceIdentity(candidate.identity());
                writes.add(candidate.product());
                newIdentities.add(candidate.identity());
                continue;
            }

            if (countedExisting.add(candidate.identity())) {
                stats.existingProductsMatched++;
            }
            boolean legacyDatasetProduct = existing.getProductUrl() != null
                    && existing.getSourceIdentity() == null;
            if (legacyDatasetProduct) {
                existing.setSourceIdentity(candidate.identity());
                writes.add(existing);
                updateIdentities.add(candidate.identity());
            } else if (candidate.quality() > productQuality(existing)) {
                copyProductData(candidate.product(), existing);
                existing.setSourceIdentity(candidate.identity());
                writes.add(existing);
                updateIdentities.add(candidate.identity());
            }
        }
        pending.clear();
        if (writes.isEmpty()) {
            return;
        }
        try {
            batchService.saveBatch(writes);
            stats.productsImported += newIdentities.size();
            stats.existingProductsUpdated += updateIdentities.size();
        } catch (RuntimeException batchError) {
            LOGGER.warn("Batch save failed; retrying {} product rows individually.", writes.size(), batchError);
            for (Product product : writes) {
                try {
                    batchService.saveOne(product);
                    if (newIdentities.contains(product.getSourceIdentity())) {
                        stats.productsImported++;
                    } else {
                        stats.existingProductsUpdated++;
                    }
                } catch (RuntimeException rowError) {
                    stats.invalidRows++;
                    LOGGER.warn("Skipping invalid product row for identity {}.",
                            product.getSourceIdentity(), rowError);
                }
            }
        }
    }

    static Candidate mapProduct(CSVRecord record, boolean master) {
        String name = clean(record, "name", "product_name", "title");
        String productUrl = validUrl(clean(record, "link", "product_url", "url"));
        String imageUrl = validUrl(clean(record, "image", "image_url", "image_link"));
        String mainCategory = clean(record, "main_category", "main category");
        String subCategory = clean(record, "sub_category", "sub category", "category");
        Double discountPrice = parseNumber(clean(record, "discount_price", "discount price", "selling_price"));
        Double listedPrice = parseNumber(clean(record, "actual_price", "actual price", "mrp"));
        Double price = discountPrice != null ? discountPrice : listedPrice;
        if (name.isBlank() || price == null || price <= 0) {
            return null;
        }

        String ratingText = clean(record, "ratings", "rating");
        Double rating = parseNumber(ratingText);
        if (rating != null && (rating < 0 || rating > 5)) {
            rating = null;
        }
        Integer ratingCount = parseCount(clean(record, "no_of_ratings", "number_of_ratings", "rating_count"));
        String stockText = clean(record, "stock", "quantity", "available_stock");
        Integer stock = parseCount(stockText);
        if (!stockText.isBlank() && stock == null) {
            return null;
        }
        Product product = new Product();
        product.setName(name);
        String description = clean(record, "description", "product_description");
        product.setDescription(description.isBlank()
                ? "Explore " + name + " in " + (subCategory.isBlank() ? "this collection" : subCategory) + "."
                : description);
        product.setPrice(price);
        product.setStock(stock == null ? 20 : stock);
        product.setCategory(subCategory.isBlank() ? mainCategory : subCategory);
        product.setMainCategory(emptyToNull(mainCategory));
        product.setSubCategory(emptyToNull(subCategory));
        product.setImageUrl(emptyToNull(imageUrl));
        product.setProductUrl(emptyToNull(productUrl));
        product.setRating(rating);
        product.setNumberOfRatings(ratingCount);
        product.setActualPrice(listedPrice != null && listedPrice > 0 ? listedPrice : price);
        String identity = identity(null, productUrl, imageUrl, name);
        if (identity == null) {
            return null;
        }
        return new Candidate(identity, product, productQuality(product), master);
    }

    private static int masterFirst(Path path) {
        return path.getFileName().toString().equalsIgnoreCase("Amazon-Products.csv") ? 0 : 1;
    }

    private static String clean(CSVRecord record, String... columns) {
        for (String column : columns) {
            if (!record.isMapped(column)) {
                continue;
            }
            String value = record.get(column);
            if (value == null) {
                continue;
            }
            value = value.trim();
            if (!value.isBlank() && !value.equalsIgnoreCase("nan")
                    && !value.equalsIgnoreCase("null")) {
                return value;
            }
        }
        return "";
    }

    private static String validUrl(String value) {
        String trimmed = value.trim();
        return trimmed.matches("(?i)^https?://\\S+$")
                ? AMAZON_IMAGE_RENDERING_PATH.matcher(trimmed).replaceFirst("$1/images/I/")
                : "";
    }

    private static String identity(String sourceIdentity, String productUrl, String imageUrl, String name) {
        if (sourceIdentity != null && !sourceIdentity.isBlank()) {
            return sourceIdentity;
        }
        String url = productUrl == null ? "" : productUrl.trim();
        Matcher asin = ASIN_PATTERN.matcher(url);
        if (asin.find()) {
            return "asin:" + asin.group(1).toUpperCase(Locale.ROOT);
        }
        if (!url.isBlank()) {
            try {
                java.net.URI uri = java.net.URI.create(url);
                return "url:" + (uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT))
                        + (uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return "url:" + url.toLowerCase(Locale.ROOT);
            }
        }
        String normalizedName = normalizeName(name);
        if (imageUrl != null && !imageUrl.isBlank()) {
            return "image-name:" + imageUrl.trim().toLowerCase(Locale.ROOT) + "|" + normalizedName;
        }
        return normalizedName.isBlank() ? null : "name:" + normalizedName;
    }

    private static Double parseNumber(String value) {
        if (value.isBlank()) {
            return null;
        }
        String numeric = value.trim().replaceAll("^[^0-9-]+", "")
                .replace(",", "").replaceAll("\\s", "");
        if (!numeric.matches("\\d+(?:\\.\\d{1,2})?")) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(numeric);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Integer parseCount(String value) {
        if (value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (!normalized.matches("(?:\\d{1,3}(?:,\\d{3})+|\\d+)")) {
            return null;
        }
        try {
            return Integer.valueOf(normalized.replace(",", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String emptyToNull(String value) {
        return value.isBlank() ? null : value;
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
    }

    private boolean isBetter(Candidate candidate, Candidate previous) {
        return candidate.quality() > previous.quality()
            || (candidate.quality() == previous.quality()
            && candidate.master() && !previous.master());
    }

    private static int productQuality(Product product) {
        int quality = 0;
        if (product.getImageUrl() != null && !product.getImageUrl().isBlank()) quality++;
        if (product.getProductUrl() != null && !product.getProductUrl().isBlank()) quality++;
        if (product.getMainCategory() != null && !product.getMainCategory().isBlank()) quality++;
        if (product.getSubCategory() != null && !product.getSubCategory().isBlank()) quality++;
        if (product.getRating() != null) quality++;
        if (product.getNumberOfRatings() != null) quality++;
        if (product.getActualPrice() != null && product.getActualPrice() > 0) quality++;
        if (product.getDescription() != null && !product.getDescription().isBlank()) quality++;
        return quality;
    }

    private void copyProductData(Product source, Product target) {
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setPrice(source.getPrice());
        target.setCategory(source.getCategory());
        target.setMainCategory(source.getMainCategory());
        target.setSubCategory(source.getSubCategory());
        target.setImageUrl(source.getImageUrl());
        target.setProductUrl(source.getProductUrl());
        target.setRating(source.getRating());
        target.setNumberOfRatings(source.getNumberOfRatings());
        target.setActualPrice(source.getActualPrice());
    }

    record Candidate(String identity, Product product, int quality, boolean master) { }

    private static final class ImportStats {
        private long filesInspected;
        private long rowsDiscovered;
        private long rowsProcessed;
        private long duplicateRowsRemoved;
        private long invalidRows;
        private long productsImported;
        private long existingProductsMatched;
        private long existingProductsUpdated;
        private long filesWithErrors;
        private long finalDatabaseProducts;
    }
}
