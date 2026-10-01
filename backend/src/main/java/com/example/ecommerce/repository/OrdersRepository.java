package com.example.ecommerce.repository;

import com.example.ecommerce.domain.Orders;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OrdersRepository extends JpaRepository<Orders, Long> {
    // orderMerchantUid 인덱스를 활용한 고속 조회 메서드
    Optional<Orders> findByOrderMerchantUid(String orderMerchantUid);
}
