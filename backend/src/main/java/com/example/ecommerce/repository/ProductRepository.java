package com.example.ecommerce.repository;

import com.example.ecommerce.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Page<Product> findAll(Pageable pageable);

    List<Product> findByNameContainingIgnoreCase(String name, Sort sort);

    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Product> findByCategory_Id(Long categoryId, Pageable pageable);

    List<Product> findByCategory_Id(Long categoryId, Sort sort);

    Page<Product> findByCategory_IdAndNameContainingIgnoreCase(Long categoryId, String name, Pageable pageable);

    List<Product> findByCategory_IdAndNameContainingIgnoreCase(Long categoryId, String name, Sort sort);

    boolean existsByCategory_Id(Long categoryId);

    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.stockQuantity = p.stockQuantity - :count where p.id = :id and p.stockQuantity >= :count")
    int decrementStockIfEnough(@Param("id") Long id, @Param("count") int count);

    @Query("select p.category.id, count(p) from Product p where p.category is not null group by p.category.id")
    List<Object[]> countGroupedByCategoryId();
}
