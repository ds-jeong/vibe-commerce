package com.example.ecommerce.controller;

import com.example.ecommerce.service.ProductLikeService;
import com.example.ecommerce.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final ProductLikeService productLikeService;

    public ProductController(ProductService productService, ProductLikeService productLikeService) {
        this.productService = productService;
        this.productLikeService = productLikeService;
    }

    /**
     * page와 size가 모두 있을 때만 페이징. 없으면 전체 리스트 반환(하위 호환).
     * keyword가 있으면 상품명 LIKE 검색.
     */
    @GetMapping
    public ResponseEntity<?> getAllProducts(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @AuthenticationPrincipal String username) {
        try {
            Object products = productService.getProducts(page, size, keyword, categoryId);
            productLikeService.attachToProducts(products, username);
            return ResponseEntity.ok(products);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "FAIL");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProduct(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(productService.getProduct(id));
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "FAIL");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
}
