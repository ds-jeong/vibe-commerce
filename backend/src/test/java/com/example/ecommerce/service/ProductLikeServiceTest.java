package com.example.ecommerce.service;

import com.example.ecommerce.domain.Product;
import com.example.ecommerce.domain.User;
import com.example.ecommerce.repository.ProductLikeRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.example.ecommerce.domain.ProductLike;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductLikeServiceTest {

    @Mock
    private ProductLikeRepository productLikeRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;

    private ProductLikeService service;

    @BeforeEach
    void setUp() {
        service = new ProductLikeService(productLikeRepository, productRepository, userRepository);
    }

    @Test
    void memberCanLikeAndCountComesFromStoredRows() {
        when(userRepository.findByUserKey("member")).thenReturn(Optional.of(user(4L)));
        when(productRepository.existsById(8L)).thenReturn(true);
        when(productLikeRepository.countByProduct_Id(8L)).thenReturn(3L);
        when(productLikeRepository.existsByUser_IdAndProduct_Id(4L, 8L)).thenReturn(true);

        Map<String, Object> liked = service.like("member", 8L);

        verify(productLikeRepository).insertIgnore(4L, 8L);
        assertEquals(8L, liked.get("productId"));
        assertEquals(3L, liked.get("likeCount"));
        assertEquals(true, liked.get("likedByCurrentUser"));
    }

    @Test
    void memberCanUnlikeOnlyTheirOwnRow() {
        when(userRepository.findByUserKey("member")).thenReturn(Optional.of(user(4L)));
        when(productRepository.existsById(8L)).thenReturn(true);
        when(productLikeRepository.countByProduct_Id(8L)).thenReturn(2L);
        when(productLikeRepository.existsByUser_IdAndProduct_Id(4L, 8L)).thenReturn(false);

        Map<String, Object> result = service.unlike("member", 8L);

        verify(productLikeRepository).deleteByUser_IdAndProduct_Id(4L, 8L);
        verify(productLikeRepository, never()).deleteByUser_IdAndProduct_Id(9L, 8L);
        assertEquals(2L, result.get("likeCount"));
        assertEquals(false, result.get("likedByCurrentUser"));
    }

    @Test
    void duplicateInsertDoesNotCreateAnotherRow() {
        when(userRepository.findByUserKey("member")).thenReturn(Optional.of(user(4L)));
        when(productRepository.existsById(8L)).thenReturn(true);
        when(productLikeRepository.insertIgnore(4L, 8L)).thenReturn(0);
        when(productLikeRepository.countByProduct_Id(8L)).thenReturn(1L);
        when(productLikeRepository.existsByUser_IdAndProduct_Id(4L, 8L)).thenReturn(true);

        Map<String, Object> result = service.like("member", 8L);

        assertEquals(1L, result.get("likeCount"));
        assertEquals(true, result.get("likedByCurrentUser"));
    }

    @Test
    void missingProductAndGuestAreRejected() {
        when(productRepository.existsById(99L)).thenReturn(false);
        when(userRepository.findByUserKey("member")).thenReturn(Optional.of(user(4L)));

        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> service.like("member", 99L));
        assertEquals("상품을 찾을 수 없습니다.", missing.getMessage());

        IllegalArgumentException guest = assertThrows(
                IllegalArgumentException.class,
                () -> service.like(null, 8L));
        IllegalArgumentException anonymous = assertThrows(
                IllegalArgumentException.class,
                () -> service.unlike("anonymousUser", 8L));
        assertEquals("회원 정보를 찾을 수 없습니다.", guest.getMessage());
        assertEquals("회원 정보를 찾을 수 없습니다.", anonymous.getMessage());
        verify(productLikeRepository, never()).insertIgnore(anyLong(), anyLong());
        verify(productLikeRepository, never()).deleteByUser_IdAndProduct_Id(anyLong(), anyLong());
    }

    @Test
    void productListAttachesCountsOnlyForTheAuthenticatedUser() {
        Product liked = product(1L);
        Product other = product(2L);
        when(userRepository.findByUserKey("member")).thenReturn(Optional.of(user(4L)));
        when(productLikeRepository.countGroupedByProductId(List.of(1L, 2L)))
                .thenReturn(List.of(new Object[]{1L, 2L}, new Object[]{2L, 5L}));
        when(productLikeRepository.findProductIdsByUserIdAndProductIdIn(4L, List.of(1L, 2L)))
                .thenReturn(List.of(1L));

        service.attachToProducts(List.of(liked, other), "member");

        assertEquals(2L, liked.getLikeCount());
        assertTrue(liked.getLikedByCurrentUser());
        assertEquals(5L, other.getLikeCount());
        assertFalse(other.getLikedByCurrentUser());

        Product guestProduct = product(1L);
        service.attachToProducts(new PageImpl<>(List.of(guestProduct)), null);
        service.attachToProducts(List.of(guestProduct), "anonymousUser");

        assertNull(guestProduct.getLikeCount());
        assertNull(guestProduct.getLikedByCurrentUser());
        verify(productLikeRepository, never()).countGroupedByProductId(List.of(1L));
    }

    @Test
    void memberSeesOnlyTheirLikedProducts() {
        Product product = product(8L);
        product.setPrice(new BigDecimal("40000"));
        product.setStockQuantity(3);
        ProductLike like = new ProductLike();
        like.setProduct(product);
        like.setCreatedAt(LocalDateTime.of(2026, 10, 22, 1, 2));
        when(userRepository.findByUserKey("member")).thenReturn(Optional.of(user(4L)));
        when(productLikeRepository.findByUser_Id(eq(4L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(like)));
        when(productLikeRepository.countGroupedByProductId(anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[]{8L, 2L}));

        Map<String, Object> body = service.likedProducts("member", 0, 8);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
        assertEquals(1, content.size());
        assertEquals(8L, content.get(0).get("id"));
        assertEquals(true, content.get(0).get("likedByCurrentUser"));
        assertEquals(2L, content.get(0).get("likeCount"));
        assertEquals("2026-10-22T01:02", content.get(0).get("likedAt"));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(productLikeRepository).findByUser_Id(eq(4L), pageable.capture());
        assertEquals(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")), pageable.getValue().getSort());
    }

    @Test
    void guestCannotListLikedProducts() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.likedProducts("anonymousUser", 0, 8));
        assertEquals("회원 정보를 찾을 수 없습니다.", error.getMessage());
        verify(productLikeRepository, never()).findByUser_Id(anyLong(), any());
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUserKey("member");
        return user;
    }

    private Product product(Long id) {
        Product product = new Product();
        product.setId(id);
        product.setName("상품" + id);
        return product;
    }
}
