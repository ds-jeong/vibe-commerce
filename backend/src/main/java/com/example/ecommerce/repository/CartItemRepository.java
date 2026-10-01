package com.example.ecommerce.repository;

import com.example.ecommerce.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    
    // ✨ [완벽 보정]: 컴파일러가 findByUsername 식별자를 정확히 인지하도록 추상 쿼리 메소드를 선언합니다!
    // 스프링 Data JPA 가 이 명세를 보고 런타임 시점에 'WHERE username = ?' SQL 문을 하드웨어 단에 자동으로 쏘아줍니다.
    List<CartItem> findByUsername(String username);
}
