package com.example.ecommerce.controller;

import com.example.ecommerce.domain.Orders;
import com.example.ecommerce.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
// @RequestMapping("/api/orders") 대신 기본 루트로 비워두거나, 아래 메서드에서 절대경로를 잡는 것이 안전합니다.
@RequestMapping
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // 1. 프론트엔드가 요청하는 정확한 절대 경로(/api/orders/place) 매핑
    @PostMapping("/api/orders/place")
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody Map<String, Object> params) {
        Map<String, Object> response = orderService.createOrder(params);
        return ResponseEntity.ok(response);
    }

    // 2. 결제 완료 후 프론트엔드가 검증 요청하는 경로 매핑
    @PostMapping("/api/orders/verify")
    public ResponseEntity<String> verifyPayment(@RequestBody Map<String, Object> params) {
        orderService.verifyAndCompletePayment(params);
        return ResponseEntity.ok("success");
    }

    // 3. 비회원 주문조회 로그인 경로 매핑
    @PostMapping("/api/orders/non-user/login")
    public ResponseEntity<Orders> loginNonUser(@RequestBody Map<String, String> params) {
        return ResponseEntity.ok(orderService.verifyNonUserOrder(params));
    }
}
