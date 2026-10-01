package com.example.ecommerce.repository;

import com.example.ecommerce.domain.Inquiry;
import com.example.ecommerce.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {
    List<Inquiry> findByUserOrderByCreatedAtDesc(User user);

    @Query("select distinct i from Inquiry i left join fetch i.user order by i.createdAt desc")
    List<Inquiry> findAllWithUser();

    @Query("select i from Inquiry i left join fetch i.user where i.id = :id")
    Optional<Inquiry> findWithUserById(@Param("id") Long id);
}
