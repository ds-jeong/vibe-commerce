package com.example.ecommerce.controller;

import com.example.ecommerce.global.PagingSupport;
import com.example.ecommerce.service.InquiryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/inquiries")
public class AdminInquiryController {

    private final InquiryService inquiryService;

    public AdminInquiryController(InquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        List<Map<String, Object>> inquiries = inquiryService.getAllInquiriesForAdmin();
        if (PagingSupport.isPaged(page, size)) {
            return ResponseEntity.ok(PagingSupport.slice(inquiries, page, size));
        }
        return ResponseEntity.ok(inquiries);
    }

    @PutMapping("/{id}/answer")
    public ResponseEntity<?> answer(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        try {
            return ResponseEntity.ok(inquiryService.answerInquiry(id, body));
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "FAIL");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "FAIL");
            response.put("message", e.getMessage() == null ? "문의 답변 처리 중 오류가 발생했습니다." : e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @DeleteMapping("/{id}/answer")
    public ResponseEntity<?> deleteAnswer(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(inquiryService.deleteAnswer(id));
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "FAIL");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
}
