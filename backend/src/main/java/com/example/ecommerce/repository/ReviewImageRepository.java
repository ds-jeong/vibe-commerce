package com.example.ecommerce.repository;

import com.example.ecommerce.domain.ReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReviewImageRepository extends JpaRepository<ReviewImage, Long> {

    List<ReviewImage> findByReview_IdInOrderBySortOrderAscIdAsc(Collection<Long> reviewIds);

    List<ReviewImage> findByReview_IdOrderBySortOrderAscIdAsc(Long reviewId);

    void deleteByReview_Id(Long reviewId);
}
