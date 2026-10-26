package com.ecommerce.productordermanagement.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.productordermanagement.dto.PlaceOrderRequest;
import com.ecommerce.productordermanagement.entity.Order;
import com.ecommerce.productordermanagement.entity.User;
import com.ecommerce.productordermanagement.exception.UnauthorizedException;
import com.ecommerce.productordermanagement.service.OrderService;
import com.ecommerce.productordermanagement.service.SessionUserService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final SessionUserService sessionUserService;

    public OrderController(OrderService orderService,
                           SessionUserService sessionUserService) {
        this.orderService = orderService;
        this.sessionUserService = sessionUserService;
    }

    @PostMapping
    public Order saveOrder(@Valid @RequestBody PlaceOrderRequest request,
                           HttpSession session) {
        return orderService.placeOrder(sessionUserService.requireUser(session),
            request.addressId(), request.normalizedItems(), request.payment());
    }

    @GetMapping
    public List<Order> getAllOrders(HttpSession session) {
        User user = sessionUserService.requireUser(session);
        return "ADMIN".equalsIgnoreCase(user.getRole())
            ? orderService.getAllOrders()
            : orderService.getOrdersForUser(user);
    }

    @GetMapping("/{id}")
    public Order getOrderById(@PathVariable Long id, HttpSession session) {
        User user = sessionUserService.requireUser(session);
        return "ADMIN".equalsIgnoreCase(user.getRole())
            ? orderService.getOrderById(id)
            : orderService.getOrderForUser(user, id);
    }

    @PutMapping("/{id}")
    public Order updateOrder(
            @PathVariable Long id,
            @RequestBody Order order,
            HttpSession session) {

        requireAdmin(session);
        return orderService.updateOrder(id, order);
    }

    @DeleteMapping("/{id}")
    public String deleteOrder(@PathVariable Long id, HttpSession session) {
        requireAdmin(session);
        orderService.deleteOrder(id);
        return "Order deleted successfully.";
    }

    private void requireAdmin(HttpSession session) {
        User user = sessionUserService.requireUser(session);
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new UnauthorizedException("Only administrators can modify orders.");
        }
    }
}