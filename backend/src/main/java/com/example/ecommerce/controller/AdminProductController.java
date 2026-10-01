package com.example.ecommerce.controller;

import com.example.ecommerce.global.PagingSupport;
import com.example.ecommerce.service.FileStorageService;
import com.example.ecommerce.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final ProductService productService;
    private final FileStorageService fileStorageService;

    public AdminProductController(ProductService productService, FileStorageService fileStorageService) {
        this.productService = productService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword) {
        if (PagingSupport.isPaged(page, size)) {
            return ResponseEntity.ok(productService.getProducts(page, size, keyword));
        }
        Object products = productService.getProducts(null, null, keyword);
        return ResponseEntity.ok(products);
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("imageUrl", fileStorageService.store(file));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        } catch (IllegalStateException e) {
            return fail(e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> payload) {
        try {
            return ResponseEntity.ok(productService.createProduct(payload));
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        try {
            return ResponseEntity.ok(productService.updateProduct(id, payload));
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            productService.deleteProduct(id);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("message", "상품이 삭제되었습니다.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> fail(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "FAIL");
        response.put("message", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
