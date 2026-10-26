package com.ecommerce.productordermanagement.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.productordermanagement.dto.SellerOrderResponse;
import com.ecommerce.productordermanagement.dto.SellerProductRequest;
import com.ecommerce.productordermanagement.dto.SellerProductResponse;
import com.ecommerce.productordermanagement.dto.SellerStatsResponse;
import com.ecommerce.productordermanagement.entity.Order;
import com.ecommerce.productordermanagement.entity.OrderItem;
import com.ecommerce.productordermanagement.entity.Product;
import com.ecommerce.productordermanagement.entity.Shop;
import com.ecommerce.productordermanagement.entity.User;
import com.ecommerce.productordermanagement.exception.ResourceNotFoundException;
import com.ecommerce.productordermanagement.repository.OrderItemRepository;
import com.ecommerce.productordermanagement.repository.ProductRepository;

@Service
public class SellerManagementService {

    private final ShopService shopService;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    public SellerManagementService(ShopService shopService, ProductRepository productRepository,
                                   OrderItemRepository orderItemRepository) {
        this.shopService = shopService;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional(readOnly = true)
    public SellerStatsResponse getStats(User seller) {
        Shop shop = shopService.requireShopForSeller(seller);
        Double revenue = orderItemRepository.sumSellerRevenue(shop.getId());
        return new SellerStatsResponse(productRepository.countByShopId(shop.getId()),
                productRepository.countByShopIdAndStockLessThanEqual(shop.getId(), 5),
                orderItemRepository.countSellerOrders(shop.getId()), revenue == null ? 0 : revenue);
    }

    @Transactional(readOnly = true)
    public List<SellerProductResponse> getProducts(User seller) {
        Shop shop = shopService.requireShopForSeller(seller);
        return productRepository.findAllByShopIdOrderByIdDesc(shop.getId()).stream()
                .map(SellerProductResponse::from).toList();
    }

    @Transactional
    public SellerProductResponse createProduct(User seller, SellerProductRequest request) {
        Shop shop = shopService.requireShopForSeller(seller);
        Product product = new Product();
        applyProductRequest(product, request);
        product.setShop(shop);
        return SellerProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public SellerProductResponse updateProduct(User seller, Long productId, SellerProductRequest request) {
        Shop shop = shopService.requireShopForSeller(seller);
        Product product = productRepository.findByIdAndShopId(productId, shop.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        applyProductRequest(product, request);
        return SellerProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(User seller, Long productId) {
        Shop shop = shopService.requireShopForSeller(seller);
        Product product = productRepository.findByIdAndShopId(productId, shop.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        productRepository.delete(product);
    }

    @Transactional(readOnly = true)
    public List<SellerOrderResponse> getOrders(User seller) {
        Shop shop = shopService.requireShopForSeller(seller);
        Long shopId = shop.getId();
        return orderItemRepository.findSellerOrders(shopId).stream()
                .map(order -> toSellerOrder(order, shopId)).toList();
    }

    private void applyProductRequest(Product product, SellerProductRequest request) {
        product.setName(request.name().trim());
        product.setDescription(request.description().trim());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setCategory(request.category().trim());
        product.setMainCategory(cleanOptional(request.mainCategory()));
        product.setSubCategory(cleanOptional(request.subCategory()));
        product.setImageUrl(cleanOptional(request.imageUrl()));
    }

    private String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private SellerOrderResponse toSellerOrder(Order order, Long shopId) {
        List<SellerOrderResponse.Item> items = order.getItems().stream()
                .filter(item -> item.getShop() != null && shopId.equals(item.getShop().getId()))
                .map(this::toSellerOrderItem).toList();
        double sellerSubtotal = items.stream().mapToDouble(SellerOrderResponse.Item::lineTotal).sum();
        return new SellerOrderResponse(order.getId(), order.getCustomerName(), order.getOrderStatus(),
                order.getPaymentMethod(), order.getPaymentStatus(), sellerSubtotal, order.getCreatedAt(),
                order.getDeliveryFullName(), order.getDeliveryPhone(), order.getDeliveryHouseNumber(),
                order.getDeliveryStreet(), order.getDeliveryArea(), order.getDeliveryCity(),
                order.getDeliveryState(), order.getDeliveryPincode(), order.getDeliveryLandmark(),
                order.getDeliveryAddressType(), items);
    }

    private SellerOrderResponse.Item toSellerOrderItem(OrderItem item) {
        return new SellerOrderResponse.Item(item.getProductId(), item.getProductName(), item.getImageUrl(),
                item.getQuantity(), item.getUnitPrice(), item.getLineTotal());
    }
}