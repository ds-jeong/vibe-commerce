package com.example.ecommerce.repository;

import com.example.ecommerce.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    // ✨ 스프링 Data JPA의 마법: Pageable 인자를 던지면 자동으로 LIMIT, OFFSET 쿼리를 컴파일하여 Page 객체로 반환합니다.
    Page<Product> findAll(Pageable pageable);
}
