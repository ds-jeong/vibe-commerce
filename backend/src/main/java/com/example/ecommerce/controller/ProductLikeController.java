package com.example.ecommerce.controller;

import com.example.ecommerce.service.ProductLikeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/products/{productId}/likes")
public class ProductLikeController {

    private final ProductLikeService productLikeService;

    public ProductLikeController(ProductLikeService productLikeService) {
        this.productLikeService = productLikeService;
    }

    @PostMapping
    public ResponseEntity<?> like(
            @PathVariable Long productId,
            @AuthenticationPrincipal String username) {
        return respond(() -> productLikeService.like(username, productId));
    }

    @DeleteMapping
    public ResponseEntity<?> unlike(
            @PathVariable Long productId,
            @AuthenticationPrincipal String username) {
        return respond(() -> productLikeService.unlike(username, productId));
    }

    private ResponseEntity<?> respond(LikeAction action) {
        try {
            return ResponseEntity.ok(action.run());
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "FAIL");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @FunctionalInterface
    private interface LikeAction {
        Map<String, Object> run();
    }
}
