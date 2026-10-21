package com.example.ecommerce.controller;

import com.example.ecommerce.service.ProductLikeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user/likes")
public class UserLikeController {

    private final ProductLikeService productLikeService;

    public UserLikeController(ProductLikeService productLikeService) {
        this.productLikeService = productLikeService;
    }

    @GetMapping
    public ResponseEntity<?> likedProducts(
            @AuthenticationPrincipal String username,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "8") Integer size) {
        try {
            return ResponseEntity.ok(productLikeService.likedProducts(username, page, size));
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "FAIL");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
}
