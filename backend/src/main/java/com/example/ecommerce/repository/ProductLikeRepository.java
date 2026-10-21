package com.example.ecommerce.repository;

import com.example.ecommerce.domain.ProductLike;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductLikeRepository extends JpaRepository<ProductLike, Long> {

    @Modifying
    @Query(value = """
            insert into product_likes (user_id, product_id, created_at)
            values (:userId, :productId, now())
            on conflict on constraint uk_product_likes_user_product do nothing
            """, nativeQuery = true)
    int insertIgnore(@Param("userId") Long userId, @Param("productId") Long productId);

    long deleteByUser_IdAndProduct_Id(Long userId, Long productId);

    @Query("select l.product.id, count(l) from ProductLike l where l.product.id in :productIds group by l.product.id")
    List<Object[]> countGroupedByProductId(@Param("productIds") Collection<Long> productIds);

    @Query("select l.product.id from ProductLike l where l.user.id = :userId and l.product.id in :productIds")
    List<Long> findProductIdsByUserIdAndProductIdIn(
            @Param("userId") Long userId,
            @Param("productIds") Collection<Long> productIds);

    Page<ProductLike> findByUser_Id(Long userId, Pageable pageable);

    long countByProduct_Id(Long productId);

    boolean existsByUser_IdAndProduct_Id(Long userId, Long productId);
}
