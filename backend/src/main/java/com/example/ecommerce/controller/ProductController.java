package com.example.ecommerce.controller;

import com.example.ecommerce.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * page와 size가 모두 있을 때만 페이징. 없으면 전체 리스트 반환(하위 호환).
     * keyword가 있으면 상품명 LIKE 검색.
     */
    @GetMapping
    public ResponseEntity<?> getAllProducts(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(productService.getProducts(page, size, keyword));
    }
}
