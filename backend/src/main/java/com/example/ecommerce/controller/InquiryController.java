package com.example.ecommerce.controller;

import com.example.ecommerce.domain.Inquiry;
import com.example.ecommerce.service.InquiryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inquiries")
public class InquiryController {

    private final InquiryService inquiryService;

    public InquiryController(InquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    @GetMapping
    public ResponseEntity<?> getMyInquiries(@AuthenticationPrincipal String username) {
        try {
            List<Inquiry> inquiries = inquiryService.getMyInquiries(username);
            return ResponseEntity.ok(inquiries);
        } catch (IllegalArgumentException e) {
            return fail(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> createInquiry(
            @AuthenticationPrincipal String username,
            @RequestBody Map<String, String> payload) {
        try {
            Inquiry inquiry = inquiryService.createInquiry(username, payload);
            return ResponseEntity.ok(inquiry);
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
