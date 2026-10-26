package com.ecommerce.productordermanagement.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String customerName;
    private String productName;
    private Long productId;
    private int quantity;
    private double totalPrice;
    private String orderStatus;
    @Column(length = 24)
    private String paymentMethod;
    @Column(length = 16)
    private String paymentStatus;
    @Column(length = 64)
    private String paymentTransactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @Column(length = 120)
    private String deliveryFullName;

    @Column(length = 10)
    private String deliveryPhone;

    @Column(length = 80)
    private String deliveryHouseNumber;

    @Column(length = 160)
    private String deliveryStreet;

    @Column(length = 120)
    private String deliveryArea;

    @Column(length = 100)
    private String deliveryCity;

    @Column(length = 100)
    private String deliveryState;

    @Column(length = 6)
    private String deliveryPincode;

    @Column(length = 160)
    private String deliveryLandmark;

    @Column(length = 16)
    private String deliveryAddressType;

    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<OrderItem> items = new ArrayList<>();

    // Default constructor
    public Order() {
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public String getPaymentTransactionId() { return paymentTransactionId; }
    public void setPaymentTransactionId(String paymentTransactionId) {
        this.paymentTransactionId = paymentTransactionId;
    }

    @PrePersist
    public void setCreatedAtIfMissing() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getDeliveryFullName() { return deliveryFullName; }
    public void setDeliveryFullName(String value) { this.deliveryFullName = value; }
    public String getDeliveryPhone() { return deliveryPhone; }
    public void setDeliveryPhone(String value) { this.deliveryPhone = value; }
    public String getDeliveryHouseNumber() { return deliveryHouseNumber; }
    public void setDeliveryHouseNumber(String value) { this.deliveryHouseNumber = value; }
    public String getDeliveryStreet() { return deliveryStreet; }
    public void setDeliveryStreet(String value) { this.deliveryStreet = value; }
    public String getDeliveryArea() { return deliveryArea; }
    public void setDeliveryArea(String value) { this.deliveryArea = value; }
    public String getDeliveryCity() { return deliveryCity; }
    public void setDeliveryCity(String value) { this.deliveryCity = value; }
    public String getDeliveryState() { return deliveryState; }
    public void setDeliveryState(String value) { this.deliveryState = value; }
    public String getDeliveryPincode() { return deliveryPincode; }
    public void setDeliveryPincode(String value) { this.deliveryPincode = value; }
    public String getDeliveryLandmark() { return deliveryLandmark; }
    public void setDeliveryLandmark(String value) { this.deliveryLandmark = value; }
    public String getDeliveryAddressType() { return deliveryAddressType; }
    public void setDeliveryAddressType(String value) { this.deliveryAddressType = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }
}