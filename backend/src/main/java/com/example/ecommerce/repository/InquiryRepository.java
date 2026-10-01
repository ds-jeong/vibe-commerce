package com.example.ecommerce.repository;

import com.example.ecommerce.domain.Inquiry;
import com.example.ecommerce.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {
    List<Inquiry> findByUserOrderByCreatedAtDesc(User user);
}
