package com.example.ecommerce.repository;

import com.example.ecommerce.domain.Payment;
import com.example.ecommerce.domain.Orders;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrder(Orders order);
}
