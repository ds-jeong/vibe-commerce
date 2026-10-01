package com.example.ecommerce.controller;

import com.example.ecommerce.domain.CartItem;
import com.example.ecommerce.service.CartService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/list")
    public ResponseEntity<List<CartItem>> getCartList(@AuthenticationPrincipal String username) {
        List<CartItem> userCart = cartService.getCartList(username);
        return ResponseEntity.ok(userCart);
    }

    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> addCartItem(
            @AuthenticationPrincipal String username,
            @RequestBody Map<String, Object> item) {
        return ResponseEntity.ok(cartService.addCartItem(username, item));
    }

    @PostMapping("/merge")
    public ResponseEntity<Map<String, Object>> mergeGuestCart(
            @AuthenticationPrincipal String username,
            @RequestBody List<Map<String, Object>> guestItems) {
        return ResponseEntity.ok(cartService.mergeGuestCart(username, guestItems));
    }

    @DeleteMapping("/{productId:\\d+}")
    public ResponseEntity<Map<String, Object>> deleteCartItem(
            @AuthenticationPrincipal String username,
            @PathVariable Long productId) {
        Map<String, Object> response = new HashMap<>();

        try {
            cartService.deleteByProductId(username, productId);
            response.put("status", "SUCCESS");
            response.put("message", "장바구니 상품이 삭제되었습니다.");
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            response.put("status", "FAIL");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearCart(@AuthenticationPrincipal String username) {
        cartService.clearCart(username);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "장바구니가 비워졌습니다.");
        return ResponseEntity.ok(response);
    }
}
