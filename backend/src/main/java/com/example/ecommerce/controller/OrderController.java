package com.example.ecommerce.controller;

import com.example.ecommerce.domain.Orders;
import com.example.ecommerce.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/api/orders/place")
    public ResponseEntity<Map<String, Object>> createOrder(
            @AuthenticationPrincipal String username,
            @RequestBody Map<String, Object> params) {
        try {
            Map<String, Object> response = orderService.createOrder(params, username);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "FAIL");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @PostMapping("/api/orders/verify")
    public ResponseEntity<String> verifyPayment(@RequestBody Map<String, Object> params) {
        orderService.verifyAndCompletePayment(params);
        return ResponseEntity.ok("success");
    }

    @PostMapping("/api/orders/non-user/login")
    public ResponseEntity<Orders> loginNonUser(@RequestBody Map<String, String> params) {
        return ResponseEntity.ok(orderService.verifyNonUserOrder(params));
    }

    @PostMapping("/api/orders/non-user/lookup")
    public ResponseEntity<?> lookupGuestOrder(@RequestBody Map<String, String> params) {
        try {
            return ResponseEntity.ok(orderService.lookupGuestOrder(params));
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "FAIL");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    @GetMapping("/api/orders/my")
    public ResponseEntity<?> getMyOrders(@AuthenticationPrincipal String username) {
        try {
            List<Orders> orders = orderService.getMyOrders(username);
            return ResponseEntity.ok(orders);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "FAIL");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }
}
