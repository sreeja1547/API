package com.ecommerce.productordermanagement.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ecommerce.productordermanagement.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

	List<Order> findAllByUserIdOrderByCreatedAtDesc(Long userId);

	Optional<Order> findByIdAndUserId(Long id, Long userId);

	long countByOrderStatus(String orderStatus);

	long countByOrderStatusIn(Collection<String> orderStatuses);

	@Query("select coalesce(sum(o.totalPrice), 0) from Order o")
	Double sumTotalPrice();

	long countByUserId(Long userId);

}