package com.example.ecommerce.repository;

import com.example.ecommerce.domain.Orders;
import com.example.ecommerce.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrdersRepository extends JpaRepository<Orders, Long> {
    Optional<Orders> findByOrderMerchantUid(String orderMerchantUid);

    @Query("select distinct o from Orders o left join fetch o.orderItems oi left join fetch oi.product where o.orderMerchantUid = :uid")
    Optional<Orders> findWithItemsByOrderMerchantUid(@Param("uid") String uid);

    @Query("select distinct o from Orders o left join fetch o.user left join fetch o.orderItems oi left join fetch oi.product where o.id = :id")
    Optional<Orders> findWithItemsById(@Param("id") Long id);

    @Query("select distinct o from Orders o left join fetch o.orderItems oi left join fetch oi.product where o.user = :user")
    List<Orders> findWithItemsByUser(@Param("user") User user);

    List<Orders> findByNonUserNameOrderByOrderDateDesc(String nonUserName);

    @Query("select distinct o from Orders o left join fetch o.orderItems oi left join fetch oi.product")
    List<Orders> findAllWithItems();
}
