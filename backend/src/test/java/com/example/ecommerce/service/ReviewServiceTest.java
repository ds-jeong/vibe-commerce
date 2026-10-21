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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private ReviewImageRepository reviewImageRepository;
    @Mock
    private FileStorageService fileStorageService;

    private ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(
                reviewRepository,
                reviewImageRepository,
                productRepository,
                userRepository,
                orderItemRepository,
                fileStorageService);
    }

    @Test
    void buyerCanCreateReviewAndClientUserIdIsIgnored() {
        when(userRepository.findByUserKey("buyer")).thenReturn(Optional.of(user(4L, "buyer", "홍길동")));
        when(productRepository.findById(8L)).thenReturn(Optional.of(product(8L)));
        when(orderItemRepository.countPurchasedByUserAndProductAndStatusIn(eq(4L), eq(8L), any())).thenReturn(1L);
        when(reviewRepository.existsByUser_IdAndProduct_Id(4L, 8L)).thenReturn(false);

        Map<String, Object> created = service.create("buyer", 8L, Map.of(
                "userId", 99,
                "rating", 5,
                "content", "  좋아요  "
        ));

        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).saveAndFlush(saved.capture());
        assertEquals(4L, saved.getValue().getUser().getId());
        assertEquals(5, saved.getValue().getRating());
        assertEquals("좋아요", saved.getValue().getContent());
        assertEquals(true, created.get("mine"));
        assertEquals("홍길동", created.get("authorName"));
    }

    @Test
    void reviewableStatusesExcludeUnpaidCancelledReturnedAndRefunded() {
        when(userRepository.findByUserKey("buyer")).thenReturn(Optional.of(user(4L, "buyer", "홍길동")));
        when(productRepository.findById(8L)).thenReturn(Optional.of(product(8L)));
        when(orderItemRepository.countPurchasedByUserAndProductAndStatusIn(eq(4L), eq(8L), any())).thenReturn(0L);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.create("buyer", 8L, Map.of("rating", 4, "content", "후기")));

        assertEquals("구매가 확인된 상품만 리뷰를 작성할 수 있습니다.", error.getMessage());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<OrderStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(orderItemRepository).countPurchasedByUserAndProductAndStatusIn(eq(4L), eq(8L), statuses.capture());
        assertTrue(statuses.getValue().contains(OrderStatus.PAID));
        assertTrue(statuses.getValue().contains(OrderStatus.DELIVERED));
        assertFalse(statuses.getValue().contains(OrderStatus.ORDERED));
        assertFalse(statuses.getValue().contains(OrderStatus.CANCELLED));
        assertFalse(statuses.getValue().contains(OrderStatus.RETURNED));
        assertFalse(statuses.getValue().contains(OrderStatus.REFUNDED));
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void guestAndUserWithoutPurchaseCannotCreate() {
        when(productRepository.findById(8L)).thenReturn(Optional.of(product(8L)));
        when(userRepository.findByUserKey("buyer")).thenReturn(Optional.of(user(4L, "buyer", "홍길동")));
        when(orderItemRepository.countPurchasedByUserAndProductAndStatusIn(eq(4L), eq(8L), any())).thenReturn(0L);

        assertThrows(IllegalArgumentException.class, () -> service.create(null, 8L, Map.of("rating", 5, "content", "후기")));
        assertThrows(IllegalArgumentException.class, () -> service.create("anonymousUser", 8L, Map.of("rating", 5, "content", "후기")));
        IllegalArgumentException noPurchase = assertThrows(
                IllegalArgumentException.class,
                () -> service.create("buyer", 8L, Map.of("rating", 5, "content", "후기")));
        assertEquals("구매가 확인된 상품만 리뷰를 작성할 수 있습니다.", noPurchase.getMessage());
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateReviewIsRejected() {
        when(userRepository.findByUserKey("buyer")).thenReturn(Optional.of(user(4L, "buyer", "홍길동")));
        when(productRepository.findById(8L)).thenReturn(Optional.of(product(8L)));
        when(orderItemRepository.countPurchasedByUserAndProductAndStatusIn(eq(4L), eq(8L), any())).thenReturn(1L);
        when(reviewRepository.existsByUser_IdAndProduct_Id(4L, 8L)).thenReturn(true);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.create("buyer", 8L, Map.of("rating", 5, "content", "또 작성")));
        assertEquals("이미 작성한 리뷰가 있습니다.", error.getMessage());

        when(reviewRepository.existsByUser_IdAndProduct_Id(4L, 8L)).thenReturn(false);
        when(reviewRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        IllegalArgumentException raced = assertThrows(
                IllegalArgumentException.class,
                () -> service.create("buyer", 8L, Map.of("rating", 5, "content", "동시 작성")));
        assertEquals("이미 작성한 리뷰가 있습니다.", raced.getMessage());
    }

    @Test
    void ratingAndContentAreValidated() {
        when(userRepository.findByUserKey("buyer")).thenReturn(Optional.of(user(4L, "buyer", "홍길동")));
        when(productRepository.findById(8L)).thenReturn(Optional.of(product(8L)));
        when(orderItemRepository.countPurchasedByUserAndProductAndStatusIn(eq(4L), eq(8L), any())).thenReturn(1L);

        assertEquals(
                "평점은 1점부터 5점까지 입력할 수 있습니다.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.create("buyer", 8L, Map.of("rating", 0, "content", "후기"))).getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> service.create("buyer", 8L, Map.of("rating", 6, "content", "후기")));
        assertEquals(
                "리뷰 내용을 입력해주세요.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.create("buyer", 8L, Map.of("rating", 3, "content", "  "))).getMessage());
        assertEquals(
                "리뷰 내용은 1000자 이내로 입력해주세요.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.create("buyer", 8L, Map.of("rating", 3, "content", "가".repeat(1001)))).getMessage());
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void onlyOwnerCanUpdateOrDelete() {
        when(userRepository.findByUserKey("other")).thenReturn(Optional.of(user(9L, "other", "김철수")));
        when(productRepository.findById(8L)).thenReturn(Optional.of(product(8L)));
        Review review = review(3L, user(4L, "buyer", "홍길동"), product(8L), 5, "원래 내용");
        when(reviewRepository.findById(3L)).thenReturn(Optional.of(review));

        ReviewAccessException updateError = assertThrows(
                ReviewAccessException.class,
                () -> service.update("other", 8L, 3L, Map.of("rating", 1, "content", "바꿔치기")));
        assertEquals("본인의 리뷰만 수정하거나 삭제할 수 있습니다.", updateError.getMessage());
        assertEquals(5, review.getRating());

        assertThrows(ReviewAccessException.class, () -> service.delete("other", 8L, 3L));
        verify(reviewRepository, never()).delete(any());
    }

    @Test
    void ownerCanUpdateOwnReview() {
        User owner = user(4L, "buyer", "홍길동");
        when(userRepository.findByUserKey("buyer")).thenReturn(Optional.of(owner));
        when(productRepository.findById(8L)).thenReturn(Optional.of(product(8L)));
        Review review = review(3L, owner, product(8L), 5, "원래 내용");
        when(reviewRepository.findById(3L)).thenReturn(Optional.of(review));

        Map<String, Object> updated = service.update("buyer", 8L, 3L, Map.of("rating", 4, "content", "수정한 내용"));

        assertEquals(4, updated.get("rating"));
        assertEquals("수정한 내용", updated.get("content"));
        assertEquals(true, updated.get("mine"));
    }

    @Test
    void listUsesNewestPageAndCalculatesAverageFromAllReviews() {
        Review older = review(1L, user(4L, "buyer", "홍길동"), product(8L), 5, "최신만 이 페이지에");
        when(productRepository.findById(8L)).thenReturn(Optional.of(product(8L)));
        when(reviewRepository.findByProduct_Id(eq(8L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(older), PageRequest.of(1, 5), 6));
        when(reviewRepository.countByProduct_Id(8L)).thenReturn(6L);
        when(reviewRepository.findAverageRatingByProductId(8L)).thenReturn(4.24);

        Map<String, Object> body = service.list(8L, null, 1, 5);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(reviewRepository).findByProduct_Id(eq(8L), pageable.capture());
        assertEquals(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")), pageable.getValue().getSort());
        assertEquals(1, pageable.getValue().getPageNumber());
        assertEquals(new BigDecimal("4.2"), body.get("averageRating"));
        assertEquals(6L, body.get("reviewCount"));
        assertEquals(2, body.get("totalPages"));
        assertEquals(false, body.get("canWrite"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
        assertEquals(1, content.size());
        verify(orderItemRepository, never()).countPurchasedByUserAndProductAndStatusIn(anyLong(), anyLong(), any());
    }

    @Test
    void purchasedMemberWithoutReviewCanWrite() {
        when(productRepository.findById(8L)).thenReturn(Optional.of(product(8L)));
        when(userRepository.findByUserKey("buyer")).thenReturn(Optional.of(user(4L, "buyer", "홍길동")));
        when(reviewRepository.findByProduct_Id(eq(8L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(reviewRepository.countByProduct_Id(8L)).thenReturn(0L);
        when(reviewRepository.existsByUser_IdAndProduct_Id(4L, 8L)).thenReturn(false);
        when(orderItemRepository.countPurchasedByUserAndProductAndStatusIn(eq(4L), eq(8L), any())).thenReturn(1L);

        Map<String, Object> body = service.list(8L, "buyer", 0, 5);

        assertEquals(true, body.get("canWrite"));
        assertEquals(false, body.get("reviewedByCurrentUser"));
        assertEquals(null, body.get("averageRating"));
        assertEquals(0L, body.get("reviewCount"));
    }

    @Test
    void reviewAcceptsAtMostFiveStoredImages() {
        when(userRepository.findByUserKey("buyer")).thenReturn(Optional.of(user(4L, "buyer", "홍길동")));
        when(productRepository.findById(8L)).thenReturn(Optional.of(product(8L)));
        when(orderItemRepository.countPurchasedByUserAndProductAndStatusIn(eq(4L), eq(8L), any())).thenReturn(1L);
        when(fileStorageService.requireReviewObjectUrl(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            review.setId(3L);
            return review;
        });

        List<String> imageUrls = List.of(
                "https://cdn.test/review/1.jpg",
                "https://cdn.test/review/2.jpg");
        Map<String, Object> created = service.create("buyer", 8L, Map.of(
                "rating", 5,
                "content", "사진 후기",
                "imageUrls", imageUrls));

        assertEquals(imageUrls, created.get("imageUrls"));
        verify(reviewImageRepository, org.mockito.Mockito.times(2)).save(any(ReviewImage.class));

        List<String> tooMany = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            tooMany.add("https://cdn.test/review/" + index + ".jpg");
        }
        IllegalArgumentException limit = assertThrows(
                IllegalArgumentException.class,
                () -> service.create("buyer", 8L, Map.of("rating", 5, "content", "너무 많음", "imageUrls", tooMany)));
        assertEquals("리뷰 이미지는 최대 5장까지 등록할 수 있습니다.", limit.getMessage());

        when(fileStorageService.requireReviewObjectUrl("https://evil.test/a.jpg"))
                .thenThrow(new IllegalArgumentException("리뷰 이미지 주소가 올바르지 않습니다."));
        IllegalArgumentException foreign = assertThrows(
                IllegalArgumentException.class,
                () -> service.create("buyer", 8L, Map.of(
                        "rating", 5,
                        "content", "외부 주소",
                        "imageUrls", List.of("https://evil.test/a.jpg"))));
        assertEquals("리뷰 이미지 주소가 올바르지 않습니다.", foreign.getMessage());
    }

    @Test
    void updateRemovesDroppedReviewImagesAndDeleteRemovesStoredFiles() {
        User owner = user(4L, "buyer", "홍길동");
        when(userRepository.findByUserKey("buyer")).thenReturn(Optional.of(owner));
        when(productRepository.findById(8L)).thenReturn(Optional.of(product(8L)));
        Review review = review(3L, owner, product(8L), 5, "원래 내용");
        when(reviewRepository.findById(3L)).thenReturn(Optional.of(review));
        when(reviewImageRepository.findByReview_IdOrderBySortOrderAscIdAsc(3L)).thenReturn(List.of(
                image(review, "https://cdn.test/review/keep.jpg"),
                image(review, "https://cdn.test/review/drop.jpg")));
        when(fileStorageService.requireReviewObjectUrl("https://cdn.test/review/keep.jpg"))
                .thenReturn("https://cdn.test/review/keep.jpg");

        service.update("buyer", 8L, 3L, Map.of(
                "rating", 4,
                "content", "수정",
                "imageUrls", List.of("https://cdn.test/review/keep.jpg")));

        verify(fileStorageService).deleteIfStored("https://cdn.test/review/drop.jpg");
        verify(fileStorageService, never()).deleteIfStored("https://cdn.test/review/keep.jpg");

        service.delete("buyer", 8L, 3L);
        verify(fileStorageService).deleteIfStored("https://cdn.test/review/keep.jpg");
        verify(reviewImageRepository, org.mockito.Mockito.atLeastOnce()).deleteByReview_Id(3L);
    }

    private User user(Long id, String userKey, String name) {
        User user = new User();
        user.setId(id);
        user.setUserKey(userKey);
        user.setName(name);
        return user;
    }

    private Product product(Long id) {
        Product product = new Product();
        product.setId(id);
        product.setName("상품" + id);
        return product;
    }

    private Review review(Long id, User user, Product product, int rating, String content) {
        Review review = new Review();
        review.setId(id);
        review.setUser(user);
        review.setProduct(product);
        review.setRating(rating);
        review.setContent(content);
        review.setCreatedAt(LocalDateTime.of(2026, 10, 22, 3, 0));
        review.setUpdatedAt(review.getCreatedAt());
        return review;
    }

    private ReviewImage image(Review review, String imageUrl) {
        ReviewImage image = new ReviewImage();
        image.setReview(review);
        image.setImageUrl(imageUrl);
        image.setSortOrder(0);
        return image;
    }
}
