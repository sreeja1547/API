package com.ecommerce.productordermanagement.service;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.productordermanagement.dto.OrderItemRequest;
import com.ecommerce.productordermanagement.dto.PaymentRequest;
import com.ecommerce.productordermanagement.entity.Order;
import com.ecommerce.productordermanagement.entity.User;

public interface OrderService {

    @Transactional
    Order saveOrder(Order order);

    Order placeOrder(User user, Long addressId, List<OrderItemRequest> items);

    Order placeOrder(User user, Long addressId, List<OrderItemRequest> items,
                     PaymentRequest payment);

    List<Order> getOrdersForUser(User user);

    Order getOrderForUser(User user, Long id);

    Order updateOrderForUser(User user, Long id, Order order);

    void deleteOrderForUser(User user, Long id);

    List<Order> getAllOrders();

    Order getOrderById(Long id);

    Order updateOrder(Long id, Order order);

    void deleteOrder(Long id);
}