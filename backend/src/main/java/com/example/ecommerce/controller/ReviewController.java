package com.example.ecommerce.controller;

import com.example.ecommerce.service.ReviewAccessException;
import com.example.ecommerce.service.ReviewService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ResponseEntity<?> list(
            @PathVariable Long productId,
            @AuthenticationPrincipal String username,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "5") Integer size) {
        try {
            return ResponseEntity.ok(reviewService.list(productId, username, page, size));
        } catch (IllegalArgumentException e) {
            return fail(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary(@PathVariable Long productId) {
        try {
            return ResponseEntity.ok(reviewService.summary(productId));
        } catch (IllegalArgumentException e) {
            return fail(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/images")
    public ResponseEntity<?> uploadImage(
            @PathVariable Long productId,
            @AuthenticationPrincipal String username,
            @RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(reviewService.uploadImage(username, productId, file));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return fail(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> create(
            @PathVariable Long productId,
            @AuthenticationPrincipal String username,
            @RequestBody(required = false) Map<String, Object> payload) {
        try {
            return ResponseEntity.ok(reviewService.create(username, productId, payload));
        } catch (IllegalArgumentException e) {
            return fail(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (DataIntegrityViolationException e) {
            return fail(HttpStatus.BAD_REQUEST, "이미 작성한 리뷰가 있습니다.");
        }
    }

    @PutMapping("/{reviewId}")
    public ResponseEntity<?> update(
            @PathVariable Long productId,
            @PathVariable Long reviewId,
            @AuthenticationPrincipal String username,
            @RequestBody(required = false) Map<String, Object> payload) {
        try {
            return ResponseEntity.ok(reviewService.update(username, productId, reviewId, payload));
        } catch (ReviewAccessException e) {
            return fail(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            return fail(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<?> delete(
            @PathVariable Long productId,
            @PathVariable Long reviewId,
            @AuthenticationPrincipal String username) {
        try {
            reviewService.delete(username, productId, reviewId);
            Map<String, Object> body = new HashMap<>();
            body.put("status", "SUCCESS");
            return ResponseEntity.ok(body);
        } catch (ReviewAccessException e) {
            return fail(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            return fail(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "FAIL");
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }
}
