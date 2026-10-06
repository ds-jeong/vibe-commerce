package com.example.ecommerce.service;

import com.example.ecommerce.domain.Product;
import com.example.ecommerce.repository.CartItemRepository;
import com.example.ecommerce.repository.OrderItemRepository;
import com.example.ecommerce.repository.ProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final FileStorageService fileStorageService;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;

    public ProductService(
            ProductRepository productRepository,
            FileStorageService fileStorageService,
            OrderItemRepository orderItemRepository,
            CartItemRepository cartItemRepository) {
        this.productRepository = productRepository;
        this.fileStorageService = fileStorageService;
        this.orderItemRepository = orderItemRepository;
        this.cartItemRepository = cartItemRepository;
    }

    @Transactional(readOnly = true)
    public Object getProducts(Integer page, Integer size, String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        Sort sort = Sort.by(Sort.Direction.DESC, "id");
        boolean paged = page != null && size != null && page >= 0 && size > 0;

        if (paged) {
            Pageable pageable = PageRequest.of(page, size, sort);
            if (trimmed.isEmpty()) {
                return productRepository.findAll(pageable);
            }
            return productRepository.findByNameContainingIgnoreCase(trimmed, pageable);
        }

        if (trimmed.isEmpty()) {
            return productRepository.findAll(sort);
        }
        return productRepository.findByNameContainingIgnoreCase(trimmed, sort);
    }

    public Product createProduct(Map<String, Object> payload) {
        Product product = new Product();
        applyProductFields(product, payload, true);
        product.setCreatedAt(java.time.LocalDateTime.now());
        return productRepository.save(product);
    }

    public Product updateProduct(Long id, Map<String, Object> payload) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        applyProductFields(product, payload, false);
        return productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        if (orderItemRepository.existsByProduct_Id(id)) {
            throw new IllegalArgumentException("주문 내역이 있는 상품은 삭제할 수 없습니다.");
        }
        cartItemRepository.deleteByProductId(id);
        fileStorageService.deleteIfStored(product.getImageUrl());
        productRepository.delete(product);
    }

    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    private void applyProductFields(Product product, Map<String, Object> payload, boolean required) {
        if (payload == null) {
            throw new IllegalArgumentException("상품 정보가 없습니다.");
        }
        Object name = payload.get("name");
        Object price = payload.get("price");
        if (required && (name == null || String.valueOf(name).isBlank() || price == null)) {
            throw new IllegalArgumentException("상품명과 가격은 필수입니다.");
        }
        if (name != null && !String.valueOf(name).isBlank()) {
            product.setName(String.valueOf(name).trim());
        }
        if (price != null) {
            product.setPrice(new java.math.BigDecimal(String.valueOf(price)));
        }
        if (payload.get("stockQuantity") != null) {
            product.setStockQuantity(((Number) payload.get("stockQuantity")).intValue());
        }
        if (payload.containsKey("imageUrl")) {
            Object imageUrl = payload.get("imageUrl");
            product.setImageUrl(imageUrl == null ? null : String.valueOf(imageUrl));
        }
        if (payload.containsKey("description")) {
            Object description = payload.get("description");
            product.setDescription(description == null ? null : String.valueOf(description));
        }
    }
}
