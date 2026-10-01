package com.example.ecommerce.service;

import com.example.ecommerce.domain.Inquiry;
import com.example.ecommerce.domain.InquiryStatus;
import com.example.ecommerce.domain.User;
import com.example.ecommerce.repository.InquiryRepository;
import com.example.ecommerce.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class InquiryService {

    private final InquiryRepository inquiryRepository;
    private final UserRepository userRepository;

    public InquiryService(InquiryRepository inquiryRepository, UserRepository userRepository) {
        this.inquiryRepository = inquiryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<Inquiry> getMyInquiries(String username) {
        User user = findUser(username);
        return inquiryRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public Inquiry createInquiry(String username, Map<String, String> payload) {
        String title = payload == null ? null : payload.get("title");
        String content = payload == null ? null : payload.get("content");

        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            throw new IllegalArgumentException("문의 제목과 내용을 입력해주세요.");
        }

        Inquiry inquiry = new Inquiry();
        inquiry.setUser(findUser(username));
        inquiry.setTitle(title.trim());
        inquiry.setContent(content.trim());
        inquiry.setStatus(InquiryStatus.PENDING);
        inquiry.setCreatedAt(LocalDateTime.now());
        return inquiryRepository.save(inquiry);
    }

    @Transactional(readOnly = true)
    public List<Inquiry> getAllInquiries() {
        return inquiryRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllInquiriesForAdmin() {
        return inquiryRepository.findAllWithUser().stream().map(this::toAdminRow).toList();
    }

    public Map<String, Object> answerInquiry(Long id, Map<String, Object> body) {
        Inquiry inquiry = inquiryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("문의글을 찾을 수 없습니다."));
        String answer = extractAnswer(body);
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("답변 내용을 입력해주세요.");
        }
        inquiry.setAnswer(answer.trim());
        inquiry.setAnsweredAt(LocalDateTime.now());
        inquiry.setStatus(InquiryStatus.ANSWERED);
        return toAdminRow(inquiry);
    }

    public Map<String, Object> deleteAnswer(Long id) {
        Inquiry inquiry = inquiryRepository.findWithUserById(id)
                .orElseThrow(() -> new IllegalArgumentException("문의글을 찾을 수 없습니다."));
        inquiry.setAnswer(null);
        inquiry.setAnsweredAt(null);
        inquiry.setStatus(InquiryStatus.PENDING);
        return toAdminRow(inquiry);
    }

    private String extractAnswer(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object raw = payload.get("answer");
        return raw == null ? null : String.valueOf(raw);
    }

    private Map<String, Object> toAdminRow(Inquiry inquiry) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", inquiry.getId());
        row.put("title", inquiry.getTitle());
        row.put("content", inquiry.getContent());
        row.put("status", inquiry.getStatus() == null ? null : inquiry.getStatus().name());
        row.put("answer", inquiry.getAnswer());
        row.put("answeredAt", inquiry.getAnsweredAt());
        row.put("createdAt", inquiry.getCreatedAt());
        User writer = inquiry.getUser();
        row.put("userKey", writer == null ? null : writer.getUserKey());
        return row;
    }

    private User findUser(String username) {
        return userRepository.findByUserKey(username)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));
    }
}
