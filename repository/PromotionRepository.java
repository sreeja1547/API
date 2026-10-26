package com.ecommerce.productordermanagement.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecommerce.productordermanagement.entity.Promotion;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    List<Promotion> findAllByOrderByIdDesc();

    @Query("select p from Promotion p where p.active = true "
            + "and (p.startsAt is null or p.startsAt <= :at) "
            + "and (p.endsAt is null or p.endsAt >= :at)")
    List<Promotion> findActiveAt(@Param("at") LocalDateTime at);
}