package com.example.ecommerce.service;

import com.example.ecommerce.domain.OrderStatus;
import com.example.ecommerce.domain.Product;
import com.example.ecommerce.domain.Review;
import com.example.ecommerce.domain.ReviewImage;
import com.example.ecommerce.domain.User;
import com.example.ecommerce.repository.OrderItemRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.ReviewImageRepository;
import com.example.ecommerce.repository.ReviewRepository;
import com.example.ecommerce.repository.UserRepository;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReviewService {

    static final int MAX_CONTENT_LENGTH = 1000;
    static final int MAX_IMAGES = 5;
    static final Set<OrderStatus> REVIEWABLE_STATUSES = EnumSet.of(
            OrderStatus.PAID,
            OrderStatus.PREPARING,
            OrderStatus.SHIPPING,
            OrderStatus.DELIVERING,
            OrderStatus.DELIVERED,
            OrderStatus.RETURN_REQUESTED,
            OrderStatus.REFUND_REQUESTED
    );

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderItemRepository orderItemRepository;
    private final FileStorageService fileStorageService;

    public ReviewService(
            ReviewRepository reviewRepository,
            ReviewImageRepository reviewImageRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            OrderItemRepository orderItemRepository,
            FileStorageService fileStorageService) {
        this.reviewRepository = reviewRepository;
        this.reviewImageRepository = reviewImageRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderItemRepository = orderItemRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(Long productId, String userKey, Integer page, Integer size) {
        requireProduct(productId);
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0 ? 5 : Math.min(size, 20);
        Page<Review> reviews = reviewRepository.findByProduct_Id(
                productId,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        User viewer = findViewer(userKey);
        Map<Long, List<String>> images = imageUrlsByReviewId(reviews.getContent());
        List<Map<String, Object>> content = new ArrayList<>();
        for (Review review : reviews.getContent()) {
            content.add(toItem(review, viewer, images.getOrDefault(review.getId(), List.of())));
        }
        Map<String, Object> body = summary(productId);
        body.put("content", content);
        body.put("totalElements", reviews.getTotalElements());
        body.put("totalPages", reviews.getTotalPages());
        body.put("number", reviews.getNumber());
        body.put("size", reviews.getSize());
        boolean alreadyReviewed = viewer != null
                && reviewRepository.existsByUser_IdAndProduct_Id(viewer.getId(), productId);
        body.put("canWrite", viewer != null && !alreadyReviewed && hasReviewablePurchase(viewer.getId(), productId));
        body.put("reviewedByCurrentUser", alreadyReviewed);
        return body;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary(Long productId) {
        requireProduct(productId);
        long reviewCount = reviewRepository.countByProduct_Id(productId);
        Double average = reviewRepository.findAverageRatingByProductId(productId);
        Map<String, Object> body = new HashMap<>();
        body.put("reviewCount", reviewCount);
        body.put("averageRating", reviewCount == 0 || average == null
                ? null
                : BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP));
        return body;
    }

    @Transactional
    public Map<String, Object> create(String userKey, Long productId, Map<String, Object> payload) {
        User user = requireUser(userKey);
        Product product = requireProduct(productId);
        if (!hasReviewablePurchase(user.getId(), productId)) {
            throw new IllegalArgumentException("구매가 확인된 상품만 리뷰를 작성할 수 있습니다.");
        }
        if (reviewRepository.existsByUser_IdAndProduct_Id(user.getId(), productId)) {
            throw new IllegalArgumentException("이미 작성한 리뷰가 있습니다.");
        }
        Review review = new Review();
        review.setUser(user);
        review.setProduct(product);
        review.setRating(parseRating(payload));
        review.setContent(parseContent(payload));
        LocalDateTime now = LocalDateTime.now();
        review.setCreatedAt(now);
        review.setUpdatedAt(now);
        List<String> imageUrls = imageUrlsFromPayload(payload);
        try {
            reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("이미 작성한 리뷰가 있습니다.");
        }
        replaceImages(review, imageUrls);
        return toItem(review, user, imageUrls);
    }

    public Map<String, Object> uploadImage(String userKey, Long productId, MultipartFile file) {
        User user = requireUser(userKey);
        requireProduct(productId);
        if (!hasReviewablePurchase(user.getId(), productId)
                && !reviewRepository.existsByUser_IdAndProduct_Id(user.getId(), productId)) {
            throw new IllegalArgumentException("구매가 확인된 상품만 리뷰 이미지를 등록할 수 있습니다.");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("imageUrl", fileStorageService.storeReviewImage(file));
        return body;
    }

    @Transactional
    public Map<String, Object> update(String userKey, Long productId, Long reviewId, Map<String, Object> payload) {
        User user = requireUser(userKey);
        requireProduct(productId);
        Review review = requireOwnReview(user, productId, reviewId);
        review.setRating(parseRating(payload));
        review.setContent(parseContent(payload));
        review.setUpdatedAt(LocalDateTime.now());
        List<String> imageUrls = payload != null && payload.containsKey("imageUrls")
                ? imageUrlsFromPayload(payload)
                : currentImageUrls(review.getId());
        if (payload != null && payload.containsKey("imageUrls")) {
            replaceImages(review, imageUrls);
        }
        return toItem(review, user, imageUrls);
    }

    @Transactional
    public void delete(String userKey, Long productId, Long reviewId) {
        User user = requireUser(userKey);
        requireProduct(productId);
        Review review = requireOwnReview(user, productId, reviewId);
        for (String imageUrl : currentImageUrls(review.getId())) {
            fileStorageService.deleteIfStored(imageUrl);
        }
        reviewImageRepository.deleteByReview_Id(review.getId());
        reviewRepository.delete(review);
    }

    private boolean hasReviewablePurchase(Long userId, Long productId) {
        return orderItemRepository.countPurchasedByUserAndProductAndStatusIn(
                userId, productId, REVIEWABLE_STATUSES) > 0;
    }

    private Review requireOwnReview(User user, Long productId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다."));
        if (review.getProduct() == null || !productId.equals(review.getProduct().getId())) {
            throw new IllegalArgumentException("리뷰를 찾을 수 없습니다.");
        }
        if (review.getUser() == null || !user.getId().equals(review.getUser().getId())) {
            throw new ReviewAccessException("본인의 리뷰만 수정하거나 삭제할 수 있습니다.");
        }
        return review;
    }

    private void replaceImages(Review review, List<String> imageUrls) {
        Set<String> kept = new HashSet<>(imageUrls);
        for (String existing : currentImageUrls(review.getId())) {
            if (!kept.contains(existing)) {
                fileStorageService.deleteIfStored(existing);
            }
        }
        reviewImageRepository.deleteByReview_Id(review.getId());
        int sortOrder = 0;
        for (String imageUrl : imageUrls) {
            ReviewImage image = new ReviewImage();
            image.setReview(review);
            image.setImageUrl(imageUrl);
            image.setSortOrder(sortOrder++);
            reviewImageRepository.save(image);
        }
    }

    private List<String> imageUrlsFromPayload(Map<String, Object> payload) {
        if (payload == null || !payload.containsKey("imageUrls") || payload.get("imageUrls") == null) {
            return List.of();
        }
        Object raw = payload.get("imageUrls");
        if (!(raw instanceof List<?> values)) {
            throw new IllegalArgumentException("리뷰 이미지 형식이 올바르지 않습니다.");
        }
        if (values.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("리뷰 이미지는 최대 5장까지 등록할 수 있습니다.");
        }
        List<String> imageUrls = new ArrayList<>();
        for (Object value : values) {
            imageUrls.add(fileStorageService.requireReviewObjectUrl(value == null ? null : String.valueOf(value)));
        }
        return imageUrls;
    }

    private List<String> currentImageUrls(Long reviewId) {
        if (reviewId == null) {
            return List.of();
        }
        List<ReviewImage> found = reviewImageRepository.findByReview_IdOrderBySortOrderAscIdAsc(reviewId);
        if (found == null) {
            return List.of();
        }
        return found.stream().map(ReviewImage::getImageUrl).toList();
    }

    private Map<Long, List<String>> imageUrlsByReviewId(List<Review> reviews) {
        List<Long> reviewIds = reviews.stream().map(Review::getId).filter(id -> id != null).toList();
        Map<Long, List<String>> images = new LinkedHashMap<>();
        if (reviewIds.isEmpty()) {
            return images;
        }
        List<ReviewImage> found = reviewImageRepository.findByReview_IdInOrderBySortOrderAscIdAsc(reviewIds);
        if (found == null) {
            return images;
        }
        for (ReviewImage image : found) {
            if (image.getReview() == null || image.getReview().getId() == null) {
                continue;
            }
            images.computeIfAbsent(image.getReview().getId(), ignored -> new ArrayList<>()).add(image.getImageUrl());
        }
        return images;
    }

    private Map<String, Object> toItem(Review review, User viewer, List<String> imageUrls) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", review.getId());
        item.put("productId", review.getProduct() == null ? null : review.getProduct().getId());
        item.put("rating", review.getRating());
        item.put("content", review.getContent());
        item.put("createdAt", review.getCreatedAt() == null ? null : review.getCreatedAt().toString());
        item.put("updatedAt", review.getUpdatedAt() == null ? null : review.getUpdatedAt().toString());
        item.put("imageUrls", imageUrls == null ? List.of() : imageUrls);
        User author = review.getUser();
        item.put("authorName", author == null || author.getName() == null || author.getName().isBlank()
                ? "회원"
                : author.getName());
        item.put("mine", viewer != null && author != null && viewer.getId().equals(author.getId()));
        return item;
    }

    private int parseRating(Map<String, Object> payload) {
        Object raw = payload == null ? null : payload.get("rating");
        int rating;
        if (raw instanceof Number number) {
            rating = number.intValue();
        } else {
            try {
                rating = Integer.parseInt(raw == null ? "" : String.valueOf(raw).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("평점은 1점부터 5점까지 입력할 수 있습니다.");
            }
        }
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("평점은 1점부터 5점까지 입력할 수 있습니다.");
        }
        return rating;
    }

    private String parseContent(Map<String, Object> payload) {
        Object raw = payload == null ? null : payload.get("content");
        String content = raw == null ? "" : String.valueOf(raw).trim();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("리뷰 내용을 입력해주세요.");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("리뷰 내용은 1000자 이내로 입력해주세요.");
        }
        return content;
    }

    private Product requireProduct(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다.");
        }
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
    }

    private User requireUser(String userKey) {
        if (isGuest(userKey)) {
            throw new IllegalArgumentException("회원 정보를 찾을 수 없습니다.");
        }
        return userRepository.findByUserKey(userKey)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));
    }

    private User findViewer(String userKey) {
        if (isGuest(userKey)) {
            return null;
        }
        return userRepository.findByUserKey(userKey).orElse(null);
    }

    private boolean isGuest(String userKey) {
        return userKey == null || userKey.isBlank() || "anonymousUser".equals(userKey);
    }
}
