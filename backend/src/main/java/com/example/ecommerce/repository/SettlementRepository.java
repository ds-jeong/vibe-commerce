package com.example.ecommerce.repository;

import com.example.ecommerce.domain.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    Optional<Settlement> findByOrder_Id(Long orderId);
}
