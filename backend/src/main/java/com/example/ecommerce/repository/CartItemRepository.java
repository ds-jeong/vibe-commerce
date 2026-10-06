package com.example.ecommerce.repository;

import com.example.ecommerce.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByUsername(String username);

    List<CartItem> findByUsernameAndProductId(String username, Long productId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CartItem c where c.username = :username and c.productId = :productId")
    int deleteByUsernameAndProductId(@Param("username") String username, @Param("productId") Long productId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CartItem c where c.username = :username")
    int deleteByUsername(@Param("username") String username);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CartItem c where c.productId = :productId")
    int deleteByProductId(@Param("productId") Long productId);
}
