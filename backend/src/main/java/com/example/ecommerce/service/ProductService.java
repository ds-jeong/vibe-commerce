package com.example.ecommerce.service;

import com.example.ecommerce.domain.Product;
import com.example.ecommerce.repository.ProductRepository;
import org.springframework.data.domain.Page;
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

    // ✨ 롬복 의존성 락을 차단하기 위해 명시적 수동 생성자 주입 명세 수립
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Page<Product> getProducts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return productRepository.findAll(pageable);
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

    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다.");
        }
        productRepository.deleteById(id);
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
