package com.ecommerce.productordermanagement.controller;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.productordermanagement.entity.Order;
import com.ecommerce.productordermanagement.entity.Product;
import com.ecommerce.productordermanagement.entity.User;
import com.ecommerce.productordermanagement.exception.ResourceNotFoundException;
import com.ecommerce.productordermanagement.repository.OrderRepository;
import com.ecommerce.productordermanagement.repository.ProductRepository;
import com.ecommerce.productordermanagement.repository.UserRepository;
import com.ecommerce.productordermanagement.service.SessionUserService;
import com.ecommerce.productordermanagement.service.PromotionService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private static final Set<String> VALID_STATUSES = Set.of(
            "PLACED", "CONFIRMED", "SHIPPED", "OUT FOR DELIVERY", "DELIVERED", "CANCELLED"
    );

    private static final Set<String> ORDER_STATUS_TRANSITIONS = Set.of(
            "PLACED->CONFIRMED",
            "PLACED->CANCELLED",
            "CONFIRMED->SHIPPED",
            "CONFIRMED->CANCELLED",
            "SHIPPED->OUT FOR DELIVERY",
            "SHIPPED->CANCELLED",
            "OUT FOR DELIVERY->DELIVERED",
            "OUT FOR DELIVERY->CANCELLED"
    );

    private final SessionUserService sessionUserService;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final PromotionService promotionService;

    public AdminController(SessionUserService sessionUserService,
                          ProductRepository productRepository,
                          OrderRepository orderRepository,
                          UserRepository userRepository,
                          PromotionService promotionService) {
        this.sessionUserService = sessionUserService;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.promotionService = promotionService;
    }

    @GetMapping("/stats")
    public DashboardStats getDashboardStats(HttpSession session) {
        requireAdmin(session);
        double revenue = OptionalNumber.orZero(orderRepository.sumTotalPrice());
        return new DashboardStats(
                productRepository.count(),
                orderRepository.count(),
                userRepository.countByRole("CUSTOMER"),
                revenue,
                orderRepository.countByOrderStatusIn(List.of("PLACED", "CONFIRMED", "SHIPPED", "OUT FOR DELIVERY")),
                orderRepository.countByOrderStatus("DELIVERED"),
                productRepository.countByStockLessThanEqual(5),
                orderRepository.countByOrderStatus("CANCELLED")
        );
    }

    @GetMapping("/products")
    public Page<Product> getProducts(HttpSession session,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "10") int size,
                                    @RequestParam(defaultValue = "") String search,
                                    @RequestParam(defaultValue = "") String category) {
        requireAdmin(session);
        Page<Product> products = productRepository.searchProducts(
                search == null ? "" : search.trim(),
                category == null ? "" : category.trim(),
                "",
                validatedPageable(page, size, Sort.by(Sort.Direction.DESC, "id"))
        );
        promotionService.decorateProducts(products.getContent());
        return products;
    }

    @GetMapping("/orders")
    public Page<Order> getOrders(HttpSession session,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "10") int size,
                                @RequestParam(defaultValue = "") String search) {
        requireAdmin(session);
        List<Order> allOrders = orderRepository.findAll();
        List<Order> filtered = allOrders.stream()
                .filter(order -> search == null || search.isBlank() || contains(order, search.trim().toLowerCase()))
                .sorted(orderCreatedAtComparator().reversed())
                .toList();
        return pageOf(filtered, validatedPageable(page, size, Sort.unsorted()));
    }

    @GetMapping("/customers")
    public Page<CustomerSummary> getCustomers(HttpSession session,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(defaultValue = "") String search) {
        requireAdmin(session);
        List<CustomerSummary> summary = userRepository.findAll().stream()
                .filter(user -> "CUSTOMER".equalsIgnoreCase(user.getRole()))
                .map(user -> new CustomerSummary(user.getId(), user.getName(), user.getEmail(), user.getPhone(),
                        user.getRole(), orderRepository.countByUserId(user.getId())))
                .filter(summaryItem -> search == null || search.isBlank()
                        || summaryItem.email().toLowerCase().contains(search.toLowerCase())
                        || summaryItem.name().toLowerCase().contains(search.toLowerCase()))
                .sorted(Comparator.comparing(CustomerSummary::id, Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();
        return pageOf(summary, validatedPageable(page, size, Sort.unsorted()));
    }

    @PatchMapping("/orders/{id}/status")
    @Transactional
    public Order updateOrderStatus(@PathVariable Long id,
                                   @RequestBody StatusUpdateRequest request,
                                   HttpSession session) {
        requireAdmin(session);
        if (request == null || request.status() == null || request.status().isBlank()) {
            throw new IllegalArgumentException("Order status is required.");
        }
        String status = request.status().trim().toUpperCase();
        if (!VALID_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Unsupported order status: " + status);
        }
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        String previousStatus = order.getOrderStatus() == null ? "" : order.getOrderStatus().trim().toUpperCase();
        String transitionKey = previousStatus + "->" + status;
        if (previousStatus.equals(status)) {
            return order;
        }
        if (!previousStatus.isEmpty() && !ORDER_STATUS_TRANSITIONS.contains(transitionKey)) {
            throw new IllegalArgumentException("Order status cannot transition from " + previousStatus + " to " + status + ".");
        }
        if ("CANCELLED".equals(status)) {
            restoreStockForOrder(order);
        }
        order.setOrderStatus(status);
        return orderRepository.save(order);
    }

    private User requireAdmin(HttpSession session) {
        User user = sessionUserService.requireUser(session);
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new AccessDeniedException("Admin access required.");
        }
        return user;
    }

    private boolean contains(Order order, String term) {
        return (order.getCustomerName() != null && order.getCustomerName().toLowerCase().contains(term))
                || (order.getProductName() != null && order.getProductName().toLowerCase().contains(term))
                || (order.getOrderStatus() != null && order.getOrderStatus().toLowerCase().contains(term))
                || String.valueOf(order.getId()).contains(term);
    }

    private Comparator<Order> orderCreatedAtComparator() {
        return Comparator.comparing(Order::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                .thenComparing(Order::getId, Comparator.nullsLast(Long::compareTo)).reversed();
    }

    private Pageable validatedPageable(int page, int size, Sort sort) {
        int validatedPage = Math.max(page, 0);
        int validatedSize = Math.max(1, Math.min(size, 100));
        return PageRequest.of(validatedPage, validatedSize, sort);
    }

    private <T> PageImpl<T> pageOf(List<T> items, Pageable pageable) {
        int fromIndex = (int) pageable.getOffset();
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), items.size());
        List<T> pageItems = fromIndex >= items.size() ? List.of() : items.subList(fromIndex, toIndex);
        return new PageImpl<>(pageItems, pageable, items.size());
    }

    private void restoreStockForOrder(Order order) {
        if (order == null || "CANCELLED".equalsIgnoreCase(order.getOrderStatus())
                || order.getItems() == null || order.getItems().isEmpty()) {
            return;
        }
        for (var item : order.getItems()) {
            if (item == null || item.getProductId() == null) {
                continue;
            }
            Product product = productRepository.findByIdForUpdate(item.getProductId()).orElse(null);
            if (product != null) {
                product.setStock(product.getStock() + item.getQuantity());
                productRepository.save(product);
            }
        }
    }

    public record DashboardStats(long totalProducts, long totalOrders, long totalCustomers,
                                double totalRevenue, long pendingOrders, long deliveredOrders,
                                long lowStockProducts, long cancelledOrders) {
    }

    public record StatusUpdateRequest(String status) {
    }

    public record CustomerSummary(Long id, String name, String email, String phone,
                                 String role, long orderCount) {
    }

    private static final class OptionalNumber {
        private OptionalNumber() {
        }

        static double orZero(Double value) {
            return value == null ? 0.0 : value;
        }
    }
}
