package com.example.ecommerce.service;

import com.example.ecommerce.domain.Product;
import com.example.ecommerce.domain.ProductLike;
import com.example.ecommerce.domain.User;
import com.example.ecommerce.repository.ProductLikeRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProductLikeService {

    private final ProductLikeRepository productLikeRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ProductLikeService(
            ProductLikeRepository productLikeRepository,
            ProductRepository productRepository,
            UserRepository userRepository) {
        this.productLikeRepository = productLikeRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Map<String, Object> like(String userKey, Long productId) {
        User user = requireUser(userKey);
        requireProduct(productId);
        productLikeRepository.insertIgnore(user.getId(), productId);
        return snapshot(user.getId(), productId);
    }

    @Transactional
    public Map<String, Object> unlike(String userKey, Long productId) {
        User user = requireUser(userKey);
        requireProduct(productId);
        productLikeRepository.deleteByUser_IdAndProduct_Id(user.getId(), productId);
        return snapshot(user.getId(), productId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> likedProducts(String userKey, Integer page, Integer size) {
        User user = requireUser(userKey);
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0 ? 8 : Math.min(size, 100);
        Page<ProductLike> likes = productLikeRepository.findByUser_Id(
                user.getId(),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        List<Long> productIds = likes.getContent().stream()
                .map(ProductLike::getProduct)
                .filter(product -> product != null && product.getId() != null)
                .map(Product::getId)
                .toList();
        Map<Long, Long> counts = productIds.isEmpty() ? Map.of() : countByProductId(productIds);
        List<Map<String, Object>> content = new ArrayList<>();
        for (ProductLike like : likes.getContent()) {
            Product product = like.getProduct();
            if (product == null || product.getId() == null) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("id", product.getId());
            item.put("name", product.getName());
            item.put("price", product.getPrice());
            item.put("imageUrl", product.getImageUrl());
            item.put("stockQuantity", product.getStockQuantity());
            item.put("saleStatus", product.getSaleStatus() == null ? null : product.getSaleStatus().name());
            item.put("likeCount", counts.getOrDefault(product.getId(), 0L));
            item.put("likedByCurrentUser", true);
            item.put("likedAt", like.getCreatedAt() == null ? null : like.getCreatedAt().toString());
            content.add(item);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("content", content);
        body.put("totalElements", likes.getTotalElements());
        body.put("totalPages", likes.getTotalPages());
        body.put("number", likes.getNumber());
        body.put("size", likes.getSize());
        return body;
    }

    @Transactional(readOnly = true)
    public void attachToProducts(Object result, String userKey) {
        if (result == null || isGuest(userKey)) {
            return;
        }
        User user = userRepository.findByUserKey(userKey).orElse(null);
        if (user == null || user.getId() == null) {
            return;
        }
        List<Product> products = extractProducts(result);
        List<Long> productIds = products.stream().map(Product::getId).filter(id -> id != null).toList();
        if (productIds.isEmpty()) {
            return;
        }
        Map<Long, Long> counts = countByProductId(productIds);
        Set<Long> liked = new HashSet<>(productLikeRepository.findProductIdsByUserIdAndProductIdIn(user.getId(), productIds));
        for (Product product : products) {
            if (product.getId() == null) {
                continue;
            }
            product.setLikeCount(counts.getOrDefault(product.getId(), 0L));
            product.setLikedByCurrentUser(liked.contains(product.getId()));
        }
    }

    private Map<String, Object> snapshot(Long userId, Long productId) {
        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId);
        body.put("likeCount", productLikeRepository.countByProduct_Id(productId));
        body.put("likedByCurrentUser", productLikeRepository.existsByUser_IdAndProduct_Id(userId, productId));
        return body;
    }

    private Map<Long, Long> countByProductId(Collection<Long> productIds) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : productLikeRepository.countGroupedByProductId(productIds)) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    private List<Product> extractProducts(Object result) {
        if (result instanceof Page<?> page) {
            return page.getContent().stream().filter(Product.class::isInstance).map(Product.class::cast).toList();
        }
        if (result instanceof List<?> list) {
            return list.stream().filter(Product.class::isInstance).map(Product.class::cast).toList();
        }
        return List.of();
    }

    private User requireUser(String userKey) {
        if (isGuest(userKey)) {
            throw new IllegalArgumentException("회원 정보를 찾을 수 없습니다.");
        }
        return userRepository.findByUserKey(userKey)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));
    }

    private void requireProduct(Long productId) {
        if (productId == null || !productRepository.existsById(productId)) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다.");
        }
    }

    private boolean isGuest(String userKey) {
        return userKey == null || userKey.isBlank() || "anonymousUser".equals(userKey);
    }
}
