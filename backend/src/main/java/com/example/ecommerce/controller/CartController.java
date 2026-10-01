package com.example.ecommerce.controller;

import com.example.ecommerce.domain.CartItem;
import com.example.ecommerce.repository.CartItemRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart") // 🟢 장바구니 전용 독점 독립 권한 구역
public class CartController {

    private final CartItemRepository cartItemRepository;

    public CartController(CartItemRepository cartItemRepository) {
        this.cartItemRepository = cartItemRepository;
    }

    /**
     * 🔍 [신설] 로그인한 회원의 실제 DB 장바구니 내역 실시간 조회 API
     * 주소창: GET http://localhost:3000/api/cart/list 로 매핑됩니다.
     */
    @GetMapping("/list")
    public ResponseEntity<List<CartItem>> getCartList(@AuthenticationPrincipal String username) {
        System.out.println("=== 🔍 [장바구니 DB 조회] 회원 [" + username + "] 장부 스캔 ===");
        // 데이터베이스 하드디스크에 박제된 이 유저의 실물 장바구니 로우 전체 리턴
        List<CartItem> userCart = cartItemRepository.findByUsername(username);
        return ResponseEntity.ok(userCart);
    }

    /**
     * 🔄 비회원 장바구니 자산을 진짜 DB 원장으로 이관 병합(Merge)하는 API
     */
    @PostMapping("/merge")
    public ResponseEntity<Map<String, Object>> mergeGuestCart(
            @AuthenticationPrincipal String username, 
            @RequestBody List<Map<String, Object>> guestItems) {

        System.out.println("=== 🛒 [장바구니 도메인 독립 커널] 회원 [" + username + "] 자산 이관 시작 ===");

        for (Map<String, Object> item : guestItems) {
            Long productId = ((Number) item.get("id")).longValue();
            String productName = (String) item.get("name");
            Long price = ((Number) item.get("price")).longValue();
            int quantity = ((Number) item.get("quantity")).intValue();

            CartItem cartItem = new CartItem(username, productId, productName, price, quantity);
            cartItemRepository.save(cartItem);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "독립 패키지 검증 통과 및 [" + username + "] 회원 장바구니 이관 마감");
        return ResponseEntity.ok(response);
    }
}
