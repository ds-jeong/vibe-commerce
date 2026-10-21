package com.example.ecommerce.controller;

import com.example.ecommerce.global.PagingSupport;
import com.example.ecommerce.service.ReturnRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminReturnController {

    private final ReturnRequestService returnRequestService;

    public AdminReturnController(ReturnRequestService returnRequestService) {
        this.returnRequestService = returnRequestService;
    }

    @GetMapping("/returns")
    public ResponseEntity<?> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        List<Map<String, Object>> rows = returnRequestService.listAdminReturns();
        if (PagingSupport.isPaged(page, size)) {
            return ResponseEntity.ok(PagingSupport.slice(rows, page, size));
        }
        return ResponseEntity.ok(rows);
    }

    @PutMapping("/orders/{id}/returns/approve")
    public ResponseEntity<?> approve(@PathVariable Long id) {
        return handle(() -> returnRequestService.approve(id));
    }

    @PutMapping("/orders/{id}/returns/reject")
    public ResponseEntity<?> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return handle(() -> returnRequestService.reject(id, reason));
    }

    @PutMapping("/orders/{id}/returns/receive")
    public ResponseEntity<?> receive(@PathVariable Long id) {
        return handle(() -> returnRequestService.receive(id));
    }

    @PutMapping("/orders/{id}/returns/mock-complete")
    public ResponseEntity<?> mockComplete(@PathVariable Long id) {
        return handle(() -> returnRequestService.mockComplete(id));
    }

    private ResponseEntity<?> handle(Action action) {
        try {
            return ResponseEntity.ok(action.run());
        } catch (IllegalArgumentException e) {
            return fail(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            return fail(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "FAIL");
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }

    @FunctionalInterface
    private interface Action {
        Map<String, Object> run();
    }
}
