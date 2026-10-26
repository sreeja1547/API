package com.ecommerce.productordermanagement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecommerce.productordermanagement.entity.Order;
import com.ecommerce.productordermanagement.entity.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("select distinct sellerOrder from Order sellerOrder join sellerOrder.items item "
            + "where item.shop.id = :shopId order by sellerOrder.createdAt desc")
    List<Order> findSellerOrders(@Param("shopId") Long shopId);

    @Query("select count(distinct item.order.id) from OrderItem item where item.shop.id = :shopId")
    long countSellerOrders(@Param("shopId") Long shopId);

    @Query("select coalesce(sum(item.lineTotal), 0) from OrderItem item "
            + "where item.shop.id = :shopId and upper(item.order.orderStatus) <> 'CANCELLED'")
    Double sumSellerRevenue(@Param("shopId") Long shopId);
}