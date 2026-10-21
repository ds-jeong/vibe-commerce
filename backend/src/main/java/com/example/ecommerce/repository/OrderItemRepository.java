package com.example.ecommerce.repository;

import com.example.ecommerce.domain.OrderItem;
import com.example.ecommerce.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    boolean existsByProduct_Id(Long productId);

    @Query("""
            select count(oi) from OrderItem oi
            where oi.order.user.id = :userId
              and oi.product.id = :productId
              and oi.order.status in :statuses
            """)
    long countPurchasedByUserAndProductAndStatusIn(
            @Param("userId") Long userId,
            @Param("productId") Long productId,
            @Param("statuses") Collection<OrderStatus> statuses);
}
