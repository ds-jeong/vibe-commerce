package com.example.ecommerce.controller;

import com.example.ecommerce.domain.Product;
import com.example.ecommerce.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    // ✨ [수동 생성자 강제 바인딩] 컴파일러가 무조건 식별자를 매핑하도록 오버라이딩 오버라이딩 마감합니다.
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * 🛍️ 소비자용 상품 목록 페이징 조회 API
     * http://localhost:8080/api/products?page=0&size=8
     */
    @GetMapping
    public ResponseEntity<Page<Product>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Page<Product> productPage = productService.getProducts(page, size);
        return ResponseEntity.ok(productPage);
    }
}
