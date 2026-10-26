package com.ecommerce.productordermanagement.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.ecommerce.productordermanagement.dto.CsvCatalogProduct;
import com.ecommerce.productordermanagement.entity.Product;
import com.ecommerce.productordermanagement.exception.ResourceNotFoundException;

@Service
public class CsvProductCatalogService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SORTED_OFFSET = 50_000;

    private final Path datasetRoot;
    private final List<Path> selectedFiles;
    private final ConcurrentHashMap<String, CsvCatalogProduct> itemCache = new ConcurrentHashMap<>();
    private volatile List<String> cachedCategories = null;

    @Autowired
    public CsvProductCatalogService(
            @Value("${app.dataset.path:./dataset/amazon-dataset}") String datasetPath) {
        this(Paths.get(datasetPath));
    }

    CsvProductCatalogService(Path datasetRoot) {
        this(datasetRoot, null);
    }

    CsvProductCatalogService(Path datasetRoot, List<Path> selectedFiles) {
        this.datasetRoot = datasetRoot.toAbsolutePath().normalize();
        this.selectedFiles = selectedFiles == null ? null : List.copyOf(selectedFiles);
    }

    public Page<CsvCatalogProduct> getPage(int page, int size, String search,
                                           String category, String gender,
                                           String sortBy, String direction) {
        int pageNumber = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long offset = (long) pageNumber * pageSize;
        String normalizedSort = normalizeSort(sortBy);
        boolean ascending = !"desc".equalsIgnoreCase(direction);
        boolean naturalOrder = "id".equals(normalizedSort) && ascending;
        if (!naturalOrder && offset > MAX_SORTED_OFFSET) {
            throw new IllegalArgumentException(
                    "Sorted CSV pages are limited to the first " + MAX_SORTED_OFFSET + " results.");
        }

        Comparator<CsvCatalogProduct> comparator = comparator(normalizedSort, ascending);
        int retentionLimit = naturalOrder ? pageSize : Math.toIntExact(offset + pageSize);
        PriorityQueue<CsvCatalogProduct> retained = naturalOrder
                ? null : new PriorityQueue<>(Math.max(1, retentionLimit), comparator.reversed());
        List<CsvCatalogProduct> naturalPage = naturalOrder ? new ArrayList<>(pageSize) : null;
        Set<String> identities = new HashSet<>();
        long[] total = { 0 };
        String normalizedSearch = normalize(search);
        String normalizedCategory = normalize(category);
        String normalizedGender = normalize(gender);
        if (!normalizedGender.isEmpty()
                && !List.of("women", "men", "kids").contains(normalizedGender)) {
            throw new IllegalArgumentException("Fashion gender must be women, men, or kids.");
        }

        scan((candidate, master) -> {
            if (candidate == null || !identities.add(candidate.identity())) {
                return;
            }
            Product product = candidate.product();
            if (!matches(product, normalizedSearch, normalizedCategory, normalizedGender)) {
                return;
            }
            total[0]++;
            CsvCatalogProduct mapped = toResponse(candidate);
            itemCache.put(mapped.id(), mapped);
            if (naturalOrder) {
                if (total[0] > offset && naturalPage.size() < pageSize) {
                    naturalPage.add(mapped);
                }
            } else if (retained.size() < retentionLimit) {
                retained.add(mapped);
            } else if (comparator.compare(mapped, retained.peek()) < 0) {
                retained.poll();
                retained.add(mapped);
            }
        });

        List<CsvCatalogProduct> content;
        if (naturalOrder) {
            content = naturalPage;
        } else {
            List<CsvCatalogProduct> ordered = retained.stream().sorted(comparator).toList();
            int from = (int) Math.min(offset, ordered.size());
            int to = Math.min(from + pageSize, ordered.size());
            content = new ArrayList<>(ordered.subList(from, to));
        }

        Sort sort = ascending ? Sort.by(normalizedSort) : Sort.by(normalizedSort).descending();
        return new PageImpl<>(content, PageRequest.of(pageNumber, pageSize, sort), total[0]);
    }

    public List<String> getCategories() {
        if (cachedCategories != null) {
            return cachedCategories;
        }
        TreeSet<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        scan((candidate, master) -> {
            if (candidate == null) {
                return;
            }
            Product product = candidate.product();
            addCategory(categories, product.getCategory());
            addCategory(categories, product.getMainCategory());
            addCategory(categories, product.getSubCategory());
        });
        List<String> result = List.copyOf(categories);
        cachedCategories = result;
        return result;
    }

    public CsvCatalogProduct getById(String id) {
        if (id != null && itemCache.containsKey(id)) {
            return itemCache.get(id);
        }
        CsvCatalogProduct[] found = { null };
        scan((candidate, master) -> {
            if (candidate != null) {
                CsvCatalogProduct response = toResponse(candidate);
                itemCache.put(response.id(), response);
                if (found[0] == null && response.id().equals(id)) {
                    found[0] = response;
                }
            }
        });
        if (found[0] == null) {
            throw new ResourceNotFoundException("CSV catalogue item not found.");
        }
        return found[0];
    }

    private void scan(CandidateConsumer consumer) {
        List<Path> files = csvFiles();
        for (Path file : files) {
            boolean master = file.getFileName().toString().equalsIgnoreCase("Amazon-Products.csv");
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
                 CSVParser parser = CSVParser.parse(reader, CsvProductImporter.CSV_FORMAT)) {
                for (CSVRecord record : parser) {
                    CsvProductImporter.Candidate candidate = null;
                    try {
                        if (record.isConsistent()) {
                            candidate = CsvProductImporter.mapProduct(record, master);
                        }
                    } catch (RuntimeException ignored) {
                        // Match the importer's behavior of skipping malformed rows.
                    }
                    consumer.accept(candidate, master);
                }
            } catch (IOException exception) {
                throw new UncheckedIOException("Unable to read CSV catalogue file: " + file.getFileName(), exception);
            }
        }
    }

    private List<Path> csvFiles() {
        if (selectedFiles != null) {
            return selectedFiles.stream()
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
                .sorted(Comparator.comparingInt(CsvProductCatalogService::masterFirst)
                    .thenComparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        }
        if (!Files.isDirectory(datasetRoot)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(datasetRoot)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
                    .sorted(Comparator.comparingInt(CsvProductCatalogService::masterFirst)
                            .thenComparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to list CSV catalogue files.", exception);
        }
    }

    private boolean matches(Product product, String search, String category, String gender) {
        boolean matchesSearch = search.isEmpty()
                || contains(product.getName(), search)
                || contains(product.getDescription(), search)
                || contains(product.getCategory(), search)
                || contains(product.getMainCategory(), search)
                || contains(product.getSubCategory(), search);
        boolean matchesCategory = category.isEmpty()
                || equalsIgnoreCase(product.getCategory(), category)
                || equalsIgnoreCase(product.getMainCategory(), category)
                || equalsIgnoreCase(product.getSubCategory(), category)
                || ("electronics".equals(category) && containsAny(product,
                        "headphone", "earbud", "earphone", "speaker", "keyboard", "mouse",
                        "charger", "cable", "adapter", "power bank", "electronics"))
                || ("accessories".equals(category) && containsAny(product,
                        "headphone", "earbud", "earphone", "speaker", "keyboard", "mouse",
                        "charger", "cable", "adapter", "power bank", "accessories"));
        boolean matchesGender = gender.isEmpty() || matchesGender(product, gender);
        return matchesSearch && matchesCategory && matchesGender;
    }

    private boolean matchesGender(Product product, String gender) {
        String name = normalize(product.getName());
        String main = normalize(product.getMainCategory());
        String sub = normalize(product.getSubCategory());
        if ("women".equals(gender)) {
            return name.contains("women") || main.contains("women") || sub.contains("women")
                    || contains(name, "saree", "kurti", "dress", "jeans", "sandals", "handbag", "watch");
        }
        if ("men".equals(gender)) {
            return (name.contains("men") && !name.contains("women"))
                    || (main.contains("men") && !main.contains("women"))
                    || (sub.contains("men") && !sub.contains("women"))
                    || contains(name, "shirt", "trouser", "jeans", "wallet", "belt", "watch");
        }
        if ("kids".equals(gender)) {
            return name.contains("kids") || main.contains("kids") || sub.contains("kids")
                    || main.contains("boys") || main.contains("girls")
                    || contains(name, "kid", "boy", "girl");
        }
        return false;
    }

    private boolean contains(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(Product product, String... terms) {
        for (String term : terms) {
            if (contains(product.getName(), term)
                    || contains(product.getMainCategory(), term)
                    || contains(product.getSubCategory(), term)
                    || contains(product.getCategory(), term)) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private boolean equalsIgnoreCase(String value, String other) {
        return value != null && value.equalsIgnoreCase(other);
    }

    private void addCategory(Set<String> categories, String value) {
        if (value != null && !value.isBlank()) {
            categories.add(value);
        }
    }

    private CsvCatalogProduct toResponse(CsvProductImporter.Candidate candidate) {
        Product product = candidate.product();
        String id = "csv-" + UUID.nameUUIDFromBytes(
                candidate.identity().getBytes(StandardCharsets.UTF_8));
        return new CsvCatalogProduct(id, "CSV", product.getName(), product.getDescription(),
                product.getPrice(), product.getCategory(), product.getMainCategory(),
                product.getSubCategory(), product.getImageUrl(), product.getProductUrl(),
                product.getRating(), product.getNumberOfRatings(), product.getActualPrice());
    }

    private Comparator<CsvCatalogProduct> comparator(String sortBy, boolean ascending) {
        Comparator<CsvCatalogProduct> comparator = switch (sortBy) {
            case "price" -> Comparator.comparing(CsvCatalogProduct::price,
                    Comparator.nullsLast(Double::compareTo));
            case "name" -> Comparator.comparing(CsvCatalogProduct::name,
                    String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing(CsvCatalogProduct::id);
        };
        return ascending ? comparator : comparator.reversed();
    }

    private String normalizeSort(String sortBy) {
        if (sortBy == null) {
            return "id";
        }
        String normalized = sortBy.trim().toLowerCase(Locale.ROOT);
        return List.of("id", "price", "name").contains(normalized) ? normalized : "id";
    }

    private String normalize(String value) {
        return value == null || "all".equalsIgnoreCase(value.trim())
                ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static int masterFirst(Path path) {
        return path.getFileName().toString().equalsIgnoreCase("Amazon-Products.csv") ? 0 : 1;
    }

    @FunctionalInterface
    private interface CandidateConsumer {
        void accept(CsvProductImporter.Candidate candidate, boolean master);
    }
}
