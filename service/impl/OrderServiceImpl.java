package com.ecommerce.productordermanagement.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.productordermanagement.dto.OrderItemRequest;
import com.ecommerce.productordermanagement.dto.PaymentRequest;
import com.ecommerce.productordermanagement.dto.PaymentResult;
import com.ecommerce.productordermanagement.entity.Address;
import com.ecommerce.productordermanagement.entity.Order;
import com.ecommerce.productordermanagement.entity.OrderItem;
import com.ecommerce.productordermanagement.entity.Product;
import com.ecommerce.productordermanagement.entity.User;
import com.ecommerce.productordermanagement.exception.ResourceNotFoundException;
import com.ecommerce.productordermanagement.repository.AddressRepository;
import com.ecommerce.productordermanagement.repository.OrderRepository;
import com.ecommerce.productordermanagement.repository.ProductRepository;
import com.ecommerce.productordermanagement.repository.UserRepository;
import com.ecommerce.productordermanagement.service.OrderService;
import com.ecommerce.productordermanagement.service.PaymentService;
import com.ecommerce.productordermanagement.service.PromotionService;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final PromotionService promotionService;

    public OrderServiceImpl(OrderRepository orderRepository,
                            ProductRepository productRepository,
                            AddressRepository addressRepository,
                            UserRepository userRepository,
                            PaymentService paymentService,
                            PromotionService promotionService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
        this.paymentService = paymentService;
        this.promotionService = promotionService;
    }

    @Override
    @Transactional
    public Order saveOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order is required.");
        }
        if (order.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }

        Product product;
        if (order.getProductId() != null) {
            product = productRepository.findById(order.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product not found with id: " + order.getProductId()));
        } else {
            if (order.getProductName() == null || order.getProductName().isBlank()) {
                throw new IllegalArgumentException("Product information is required.");
            }
            product = productRepository.findFirstByNameIgnoreCaseOrderByIdAsc(order.getProductName())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product not found: " + order.getProductName()));
        }

        if (product.getStock() < order.getQuantity()) {
            throw new IllegalArgumentException("Insufficient stock for product: " + product.getName());
        }

        double totalPrice = product.getPrice() * order.getQuantity();
        order.setTotalPrice(totalPrice);
        order.setProductId(product.getId());
        order.setProductName(product.getName());
        order.setOrderStatus("PLACED");

        if (order.getPaymentMethod() == null) {
            order.setPaymentMethod("COD");
            order.setPaymentStatus("PENDING");
        }

        product.setStock(product.getStock() - order.getQuantity());
        productRepository.save(product);
        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public Order placeOrder(User user, Long addressId, List<OrderItemRequest> requestedItems) {
        return placeOrder(user, addressId, requestedItems, null);
    }

    @Override
    @Transactional
    public Order placeOrder(User user, Long addressId, List<OrderItemRequest> requestedItems,
                            PaymentRequest paymentRequest) {
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new IllegalArgumentException("Your cart is empty.");
        }
        if (user == null) {
            throw new IllegalArgumentException("User is required.");
        }

        Address address = addressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Delivery address not found."));

        Map<Long, Integer> quantities = new TreeMap<>();
        for (OrderItemRequest item : requestedItems) {
            if (item == null || item.productId() == null) {
                throw new IllegalArgumentException("Each order item needs a product.");
            }
            if (item.quantity() <= 0) {
                throw new IllegalArgumentException("Each order item needs a positive quantity.");
            }
            int quantity = item.quantity();
            if (quantities.containsKey(item.productId())) {
                quantity = Math.addExact(quantities.get(item.productId()), quantity);
            }
            quantities.put(item.productId(), quantity);
        }

        Map<Long, Product> products = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Product product = productRepository.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product not found with id: " + entry.getKey()));
            if (entry.getValue() > product.getStock()) {
                throw new IllegalArgumentException(
                        "Insufficient stock for product: " + product.getName());
            }
            products.put(entry.getKey(), product);
        }
        Map<Long, Double> unitPrices = promotionService.effectivePrices(products.values());
        double total = quantities.entrySet().stream()
                .mapToDouble(entry -> unitPrices.get(entry.getKey()) * entry.getValue())
                .sum();

        PaymentResult payment = paymentService.process(paymentRequest);

        Order order = new Order();
        User managedUser = userRepository.getReferenceById(user.getId());
        order.setUser(managedUser);
        order.setCustomerName(user.getName());
        order.setOrderStatus("PLACED");
        order.setTotalPrice(total);
        order.setPaymentMethod(payment.paymentMethod());
        order.setPaymentStatus(payment.paymentStatus());
        if (payment.transactionId() != null) {
            order.setPaymentTransactionId(payment.transactionId());
        }
        copyDeliverySnapshot(address, order);

        List<OrderItem> orderItems = new ArrayList<>();
        boolean first = true;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Product product = products.get(entry.getKey());
            int quantity = entry.getValue();

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProductId(product.getId());
            orderItem.setProductName(product.getName());
            orderItem.setImageUrl(product.getImageUrl());
            orderItem.setQuantity(quantity);
            double unitPrice = unitPrices.get(entry.getKey());
            orderItem.setUnitPrice(unitPrice);
            orderItem.setLineTotal(unitPrice * quantity);
            orderItems.add(orderItem);

            product.setStock(product.getStock() - quantity);
            productRepository.save(product);

            if (first) {
                order.setProductId(product.getId());
                order.setProductName(product.getName());
                order.setQuantity(quantity);
                first = false;
            }
        }

        order.setItems(orderItems);
        return orderRepository.save(order);
    }

    private void copyDeliverySnapshot(Address address, Order order) {
        if (address == null || order == null) {
            return;
        }
        order.setDeliveryFullName(address.getFullName());
        order.setDeliveryPhone(address.getPhone());
        order.setDeliveryHouseNumber(address.getHouseNumber());
        order.setDeliveryStreet(address.getStreet());
        order.setDeliveryArea(address.getArea());
        order.setDeliveryCity(address.getCity());
        order.setDeliveryState(address.getState());
        order.setDeliveryPincode(address.getPincode());
        order.setDeliveryLandmark(address.getLandmark());
        order.setDeliveryAddressType(address.getAddressType());
    }

    @Override
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> getOrdersForUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User is required.");
        }
        return orderRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrderForUser(User user, Long id) {
        if (user == null) {
            throw new IllegalArgumentException("User is required.");
        }
        return orderRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
    }

    @Override
    @Transactional
    public Order updateOrderForUser(User user, Long id, Order order) {
        Order existing = getOrderForUser(user, id);
        if (order == null) {
            throw new IllegalArgumentException("Order is required.");
        }
        if (order.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
        if (order.getProductName() != null) {
            existing.setProductName(order.getProductName());
        }
        existing.setQuantity(order.getQuantity());
        if (order.getTotalPrice() >= 0) {
            existing.setTotalPrice(order.getTotalPrice());
        }
        if (order.getOrderStatus() != null) {
            existing.setOrderStatus(order.getOrderStatus());
        }
        return orderRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteOrderForUser(User user, Long id) {
        Order order = getOrderForUser(user, id);
        restoreStockForOrder(order);
        orderRepository.delete(order);
    }

    @Override
    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
    }

    @Override
    public Order updateOrder(Long id, Order order) {
        Order existingOrder = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

        if (order == null) {
            throw new IllegalArgumentException("Order is required.");
        }
        if (order.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
        if (order.getProductName() != null) {
            existingOrder.setProductName(order.getProductName());
        }
        existingOrder.setQuantity(order.getQuantity());
        if (order.getTotalPrice() >= 0) {
            existingOrder.setTotalPrice(order.getTotalPrice());
        }
        if (order.getOrderStatus() != null) {
            existingOrder.setOrderStatus(order.getOrderStatus());
        }

        return orderRepository.save(existingOrder);
    }

    @Override
    @Transactional
    public void deleteOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        restoreStockForOrder(order);
        orderRepository.delete(order);
    }

    private void restoreStockForOrder(Order order) {
        if (order == null || "CANCELLED".equalsIgnoreCase(order.getOrderStatus())
                || order.getItems() == null || order.getItems().isEmpty()) {
            return;
        }
        for (OrderItem item : order.getItems()) {
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
}