package com.example.ecommerce.repository;

import com.example.ecommerce.domain.ReturnRequest;
import com.example.ecommerce.domain.ReturnStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    boolean existsByOrder_IdAndStatusIn(Long orderId, Collection<ReturnStatus> statuses);

    Optional<ReturnRequest> findFirstByOrder_IdAndStatusInOrderByIdDesc(Long orderId, Collection<ReturnStatus> statuses);

    boolean existsByOrder_Id(Long orderId);

    @Query("select distinct r from ReturnRequest r join fetch r.order o left join fetch o.orderItems oi left join fetch oi.product")
    List<ReturnRequest> findAllWithOrderItems();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ReturnRequest r set r.status = :next where r.id = :id and r.status = :expected")
    int updateStatusIfMatch(
            @Param("id") Long id,
            @Param("expected") ReturnStatus expected,
            @Param("next") ReturnStatus next);
}
