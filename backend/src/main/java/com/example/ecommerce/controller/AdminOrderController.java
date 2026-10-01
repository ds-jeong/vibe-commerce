package com.example.ecommerce.controller;

import com.example.ecommerce.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String scope) {
        return ResponseEntity.ok(
                orderService.searchAdminOrders(page, size, keyword, dateFrom, dateTo, scope)
        );
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            return ResponseEntity.ok(orderService.updateAdminOrderStatus(id, request));
        } catch (IllegalArgumentException e) {
            return fail(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            return fail(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            return fail(HttpStatus.INTERNAL_SERVER_ERROR,
                    e.getMessage() == null ? "주문 상태 변경 중 오류가 발생했습니다." : e.getMessage());
        }
    }

    @PutMapping("/{id}/return-approve")
    public ResponseEntity<?> approveReturn(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(orderService.processReturnRefundWithSafety(id));
        } catch (IllegalArgumentException e) {
            return fail(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            return fail(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            return fail(HttpStatus.INTERNAL_SERVER_ERROR,
                    e.getMessage() == null ? "환불 API 처리 실패: 기존 주문 상태가 유지됩니다." : e.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "FAIL");
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }
}
